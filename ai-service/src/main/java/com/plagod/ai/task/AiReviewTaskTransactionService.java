package com.plagod.ai.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.ai.model.AiModerationDecision;
import com.plagod.ai.model.AiModerationResult;
import com.plagod.entity.AiReviewTask;
import com.plagod.mapper.AiReviewTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class AiReviewTaskTransactionService {

    public static final String MAX_ATTEMPTS_ERROR_CODE =
            "AI_TASK_MAX_ATTEMPTS";

    private static final Pattern ERROR_CODE =
            Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

    private final AiReviewTaskMapper taskMapper;
    private final TransactionOperations transactions;
    private final ObjectMapper objectMapper;

    public AiReviewTaskTransactionService(
            AiReviewTaskMapper taskMapper,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper) {
        this.taskMapper = Objects.requireNonNull(
                taskMapper,
                "taskMapper 不能为空");
        Objects.requireNonNull(
                transactionManager,
                "transactionManager 不能为空");
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactions = transactionTemplate;
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper 不能为空");
    }

    public List<Long> findDispatchableIds(
            LocalDateTime now,
            int limit) {
        Objects.requireNonNull(now, "扫描时间不能为空");
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("扫描批次必须在 1 到 500 之间");
        }
        return taskMapper.selectDispatchableIds(now, limit);
    }

    public AiReviewTaskClaim claim(
            Long reviewTaskId,
            String workerId,
            LocalDateTime now,
            LocalDateTime leaseUntil,
            int maxAttempts) {
        validateClaim(reviewTaskId, workerId, now, leaseUntil, maxAttempts);

        return transactions.execute(status -> {
            int exhausted = taskMapper.finalizeExhausted(
                    reviewTaskId,
                    now,
                    maxAttempts,
                    MAX_ATTEMPTS_ERROR_CODE);
            requireSingleRow(exhausted, "AI 任务耗尽终态更新");
            if (exhausted == 1) {
                return null;
            }

            int claimed = taskMapper.claim(
                    reviewTaskId,
                    workerId,
                    now,
                    leaseUntil,
                    maxAttempts);
            requireSingleRow(claimed, "AI 任务 claim");
            if (claimed == 0) {
                return null;
            }

            AiReviewTask task = taskMapper.selectById(reviewTaskId);
            if (task == null
                    || !"RUNNING".equals(task.getTaskStatus())
                    || !workerId.equals(task.getWorkerId())) {
                throw new IllegalStateException("claim 后 AI 任务快照不一致");
            }
            return AiReviewTaskClaim.from(task, workerId);
        });
    }

    public AiReviewTaskDispatchOutcome completeResult(
            AiReviewTaskClaim claim,
            AiModerationResult result,
            LocalDateTime now) {
        validateClaimSnapshot(claim);
        Objects.requireNonNull(result, "AI 审核结果不能为空");
        Objects.requireNonNull(now, "完成时间不能为空");

        boolean manual =
                result.getDecision() == AiModerationDecision.MANUAL;
        String taskStatus = manual ? "MANUAL_REQUIRED" : "SUCCEEDED";
        String labelsJson = labelsJson(result);
        int confidenceBps = (int) Math.round(
                result.getConfidence() * 10000.0d);

        transactions.execute(status -> {
            int updated = taskMapper.completeResult(
                    claim.getReviewTaskId(),
                    claim.getWorkerId(),
                    claim.getVersion(),
                    taskStatus,
                    result.getDecision().name(),
                    confidenceBps,
                    labelsJson,
                    result.getReasonCode(),
                    result.getProviderRequestId(),
                    result.getModel(),
                    result.getLatencyMillis(),
                    now);
            requireOwnedClaim(updated);
            return null;
        });
        return manual
                ? AiReviewTaskDispatchOutcome.MANUAL_REQUIRED
                : AiReviewTaskDispatchOutcome.SUCCEEDED;
    }

    public AiReviewTaskDispatchOutcome completeFailure(
            AiReviewTaskClaim claim,
            String errorCode,
            LocalDateTime now,
            LocalDateTime retryAt,
            int maxAttempts) {
        validateClaimSnapshot(claim);
        validateErrorCode(errorCode);
        Objects.requireNonNull(now, "失败时间不能为空");
        Objects.requireNonNull(retryAt, "重试时间不能为空");
        if (retryAt.isBefore(now)) {
            throw new IllegalArgumentException("重试时间不能早于失败时间");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("最大尝试次数必须大于 0");
        }

        AiReviewTaskDispatchOutcome outcome =
                claim.getAttemptCount() >= maxAttempts
                        ? AiReviewTaskDispatchOutcome.MANUAL_REQUIRED
                        : AiReviewTaskDispatchOutcome.RETRY_SCHEDULED;
        transactions.execute(status -> {
            int updated = taskMapper.completeFailure(
                    claim.getReviewTaskId(),
                    claim.getWorkerId(),
                    claim.getVersion(),
                    errorCode,
                    now,
                    retryAt,
                    maxAttempts);
            requireOwnedClaim(updated);
            return null;
        });
        return outcome;
    }

    private String labelsJson(AiModerationResult result) {
        try {
            return objectMapper.writeValueAsString(result.getRiskLabels());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI 风险标签序列化失败");
        }
    }

    private void validateClaim(
            Long reviewTaskId,
            String workerId,
            LocalDateTime now,
            LocalDateTime leaseUntil,
            int maxAttempts) {
        if (reviewTaskId == null || reviewTaskId <= 0) {
            throw new IllegalArgumentException("reviewTaskId 必须为正数");
        }
        if (!StringUtils.hasText(workerId) || workerId.length() > 64) {
            throw new IllegalArgumentException("workerId 必须为 1 到 64 字符");
        }
        Objects.requireNonNull(now, "claim 时间不能为空");
        Objects.requireNonNull(leaseUntil, "leaseUntil 不能为空");
        if (!leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("leaseUntil 必须晚于 claim 时间");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("最大尝试次数必须大于 0");
        }
    }

    private void validateClaimSnapshot(AiReviewTaskClaim claim) {
        Objects.requireNonNull(claim, "AI 任务 claim 不能为空");
        if (claim.getReviewTaskId() == null
                || !StringUtils.hasText(claim.getWorkerId())) {
            throw new IllegalArgumentException("AI 任务 claim 不完整");
        }
    }

    private void validateErrorCode(String errorCode) {
        if (errorCode == null || !ERROR_CODE.matcher(errorCode).matches()) {
            throw new IllegalArgumentException("errorCode 格式非法");
        }
    }

    private void requireOwnedClaim(int updated) {
        requireSingleRow(updated, "AI 任务 finalize");
        if (updated == 0) {
            throw new IllegalStateException(
                    "AI 任务 claim 已失效或不属于当前 worker");
        }
    }

    private void requireSingleRow(int updated, String operation) {
        if (updated < 0 || updated > 1) {
            throw new IllegalStateException(operation + " 影响了非预期行数");
        }
    }
}
