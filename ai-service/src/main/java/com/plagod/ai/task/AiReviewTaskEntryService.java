package com.plagod.ai.task;

import com.plagod.entity.AiReviewTask;
import com.plagod.entity.AiPolicyVersion;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.AiPolicyVersionMapper;
import com.plagod.mapper.AiReviewTaskMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class AiReviewTaskEntryService {

    private final AiReviewTaskMapper taskMapper;
    private final AiPolicyVersionMapper policyVersionMapper;
    private final TransactionTemplate transactions;

    public AiReviewTaskEntryService(
            AiReviewTaskMapper taskMapper,
            AiPolicyVersionMapper policyVersionMapper,
            PlatformTransactionManager transactionManager) {
        this.taskMapper = Objects.requireNonNull(
                taskMapper,
                "taskMapper 不能为空");
        this.policyVersionMapper = Objects.requireNonNull(
                policyVersionMapper,
                "policyVersionMapper 不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(
                        transactionManager,
                        "transactionManager 不能为空"));
    }

    public AiReviewTaskEntryResult submit(
            AiReviewTaskSubmission submission) {
        Objects.requireNonNull(submission, "任务提交不能为空");

        try {
            return requireResult(transactions.execute(status -> {
                AiReviewTask existing = taskMapper.selectByReviewRequestId(
                        submission.getReviewRequestId());
                if (existing != null) {
                    return replay(existing, submission);
                }

                AiPolicyVersion policyVersion =
                        policyVersionMapper.selectActiveByScene(
                                submission.getScene().name());
                if (policyVersion == null
                        || policyVersion.getPolicyVersionId() == null
                        || policyVersion.getPolicyVersionId() <= 0L) {
                    throw new IllegalStateException(
                            "当前审核场景没有 ACTIVE policy version");
                }
                AiReviewTask created = newTask(
                        submission,
                        policyVersion.getPolicyVersionId());
                int inserted = taskMapper.insert(created);
                if (inserted != 1 || created.getReviewTaskId() == null) {
                    throw new IllegalStateException("AI 任务首次写入未返回持久化任务");
                }
                return new AiReviewTaskEntryResult(created, false);
            }));
        } catch (DuplicateKeyException duplicateKeyException) {
            return requireResult(transactions.execute(status -> {
                AiReviewTask winner = taskMapper.selectByReviewRequestId(
                        submission.getReviewRequestId());
                if (winner == null) {
                    throw duplicateKeyException;
                }
                return replay(winner, submission);
            }));
        }
    }

    private AiReviewTaskEntryResult replay(
            AiReviewTask existing,
            AiReviewTaskSubmission submission) {
        if (!sameFingerprint(existing, submission)) {
            throw ApiStatusException.idempotencyConflict(
                    "reviewRequestId 已用于不同的 AI 审核任务");
        }
        return new AiReviewTaskEntryResult(existing, true);
    }

    private boolean sameFingerprint(
            AiReviewTask existing,
            AiReviewTaskSubmission submission) {
        return submission.getScene().name().equals(existing.getScene())
                && submission.getBusinessType().equals(existing.getBusinessType())
                && submission.getBusinessId().equals(existing.getBusinessId())
                && Objects.equals(submission.getTenantId(), existing.getTenantId())
                && Integer.valueOf(submission.getContentVersion())
                .equals(existing.getContentVersion())
                && submission.getContentHash().equals(existing.getContentHash());
    }

    private AiReviewTask newTask(
            AiReviewTaskSubmission submission,
            Long policyVersionId) {
        LocalDateTime now = LocalDateTime.now();
        AiReviewTask task = new AiReviewTask();
        task.setReviewRequestId(submission.getReviewRequestId());
        task.setScene(submission.getScene().name());
        task.setBusinessType(submission.getBusinessType());
        task.setBusinessId(submission.getBusinessId());
        task.setTenantId(submission.getTenantId());
        task.setContentVersion(submission.getContentVersion());
        task.setContentHash(submission.getContentHash());
        task.setPolicyVersionId(policyVersionId);
        task.setTaskStatus("QUEUED");
        task.setAttemptCount(0);
        task.setNextRetryTime(now);
        task.setVersion(0);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        return task;
    }

    private AiReviewTaskEntryResult requireResult(
            AiReviewTaskEntryResult result) {
        if (result == null) {
            throw new IllegalStateException("AI 任务幂等事务未返回结果");
        }
        return result;
    }
}
