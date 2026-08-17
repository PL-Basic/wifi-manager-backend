package com.plagod.service;

import com.plagod.entity.MarketplaceFulfillment;
import com.plagod.mapper.MarketplaceFulfillmentMapper;
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
public class MarketplaceFulfillmentTransactionService {

    public static final String MAX_ATTEMPTS_ERROR_KEY =
            "FULFILLMENT_MAX_ATTEMPTS";

    private static final Pattern ERROR_KEY =
            Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

    private final MarketplaceFulfillmentMapper fulfillmentMapper;
    private final TransactionOperations transactions;

    public MarketplaceFulfillmentTransactionService(
            MarketplaceFulfillmentMapper fulfillmentMapper,
            PlatformTransactionManager transactionManager) {
        this.fulfillmentMapper = fulfillmentMapper;
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactions = transactionTemplate;
    }

    public List<Long> findDispatchableIds(LocalDateTime now, int limit) {
        Objects.requireNonNull(now, "扫描时间不能为空");
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("扫描批次必须在 1 到 500 之间");
        }
        List<Long> fulfillmentIds = transactions.execute(
                status -> fulfillmentMapper.selectDispatchableIds(now, limit));
        if (fulfillmentIds == null) {
            throw new IllegalStateException("履约扫描事务未返回结果");
        }
        return fulfillmentIds;
    }

    public MarketplaceFulfillmentClaim claim(
            Long fulfillmentId,
            String workerId,
            LocalDateTime now,
            LocalDateTime leaseUntil,
            int maxAttempts) {
        validateClaim(fulfillmentId, workerId, now, leaseUntil, maxAttempts);

        return transactions.execute(status -> {
            int exhausted = fulfillmentMapper.failExhausted(
                    fulfillmentId,
                    now,
                    maxAttempts,
                    MAX_ATTEMPTS_ERROR_KEY);
            requireSingleRow(exhausted, "耗尽履约终态更新");
            if (exhausted == 1) {
                return null;
            }

            int claimed = fulfillmentMapper.claim(
                    fulfillmentId,
                    workerId,
                    now,
                    leaseUntil,
                    maxAttempts);
            requireSingleRow(claimed, "履约 claim");
            if (claimed == 0) {
                return null;
            }

            MarketplaceFulfillment fulfillment =
                    fulfillmentMapper.selectById(fulfillmentId);
            if (fulfillment == null
                    || !"PROCESSING".equals(fulfillment.getStatus())
                    || !workerId.equals(fulfillment.getWorkerId())
                    || fulfillment.getAttemptCount() == null) {
                throw new IllegalStateException("claim 后履约快照不一致");
            }
            return MarketplaceFulfillmentClaim.from(fulfillment, workerId);
        });
    }

    public void completeSuccess(
            MarketplaceFulfillmentClaim claim,
            String resultReference,
            LocalDateTime now) {
        validateClaimSnapshot(claim);
        Objects.requireNonNull(now, "完成时间不能为空");
        if (!StringUtils.hasText(resultReference)
                || resultReference.length() > 128) {
            throw new IllegalArgumentException("resultReference 必须为 1 到 128 字符");
        }

        transactions.execute(status -> {
            int updated = fulfillmentMapper.completeSuccess(
                    claim.getFulfillmentId(),
                    claim.getWorkerId(),
                    resultReference,
                    now);
            requireOwnedClaim(updated);
            return null;
        });
    }

    public MarketplaceFulfillmentDispatchOutcome completeFailure(
            MarketplaceFulfillmentClaim claim,
            String errorKey,
            LocalDateTime now,
            LocalDateTime retryAt,
            int maxAttempts) {
        validateClaimSnapshot(claim);
        validateErrorKey(errorKey);
        Objects.requireNonNull(now, "失败时间不能为空");
        Objects.requireNonNull(retryAt, "重试时间不能为空");
        if (retryAt.isBefore(now)) {
            throw new IllegalArgumentException("重试时间不能早于失败时间");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("最大尝试次数必须大于 0");
        }

        MarketplaceFulfillmentDispatchOutcome outcome =
                claim.getAttemptCount() >= maxAttempts
                        ? MarketplaceFulfillmentDispatchOutcome.FAILED_TERMINAL
                        : MarketplaceFulfillmentDispatchOutcome.RETRY_SCHEDULED;

        transactions.execute(status -> {
            int updated = fulfillmentMapper.completeFailure(
                    claim.getFulfillmentId(),
                    claim.getWorkerId(),
                    errorKey,
                    now,
                    retryAt,
                    maxAttempts);
            requireOwnedClaim(updated);
            return null;
        });
        return outcome;
    }

    private void validateClaim(
            Long fulfillmentId,
            String workerId,
            LocalDateTime now,
            LocalDateTime leaseUntil,
            int maxAttempts) {
        if (fulfillmentId == null || fulfillmentId <= 0) {
            throw new IllegalArgumentException("fulfillmentId 必须为正数");
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

    private void validateClaimSnapshot(MarketplaceFulfillmentClaim claim) {
        Objects.requireNonNull(claim, "履约 claim 不能为空");
        if (claim.getFulfillmentId() == null
                || !StringUtils.hasText(claim.getWorkerId())) {
            throw new IllegalArgumentException("履约 claim 不完整");
        }
    }

    private void validateErrorKey(String errorKey) {
        if (errorKey == null || !ERROR_KEY.matcher(errorKey).matches()) {
            throw new IllegalArgumentException("errorKey 格式非法");
        }
    }

    private void requireOwnedClaim(int updated) {
        requireSingleRow(updated, "履约 finalize");
        if (updated == 0) {
            throw new IllegalStateException("履约 claim 已失效或不属于当前 worker");
        }
    }

    private void requireSingleRow(int updated, String operation) {
        if (updated < 0 || updated > 1) {
            throw new IllegalStateException(operation + " 影响了非预期行数");
        }
    }
}
