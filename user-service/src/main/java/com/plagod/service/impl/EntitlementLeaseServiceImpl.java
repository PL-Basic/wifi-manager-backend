package com.plagod.service.impl;

import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.dto.user.EntitlementLeaseRequest;
import com.plagod.entity.entitlement.DurationPurchase;
import com.plagod.entity.entitlement.EntitlementLeaseReceipt;
import com.plagod.entity.entitlement.EntitlementUsageLog;
import com.plagod.entity.entitlement.NetworkEntitlement;
import com.plagod.entity.user.User;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.DurationPurchaseMapper;
import com.plagod.mapper.EntitlementLeaseReceiptMapper;
import com.plagod.mapper.EntitlementUsageLogMapper;
import com.plagod.mapper.NetworkEntitlementMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.service.EntitlementLeaseService;
import com.plagod.utils.TenantScopeUtils;
import com.plagod.vo.user.EntitlementLeaseResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class EntitlementLeaseServiceImpl implements EntitlementLeaseService {

    private static final String SUBSCRIPTION = "SUBSCRIPTION";
    private static final String DURATION_MODE = "DURATION";
    private static final int MAX_TTL_SECONDS = 20;

    private final UserMapper userMapper;
    private final NetworkEntitlementMapper entitlementMapper;
    private final DurationPurchaseMapper purchaseMapper;
    private final EntitlementUsageLogMapper usageLogMapper;
    private final EntitlementLeaseReceiptMapper receiptMapper;
    private final TransactionTemplate transactionTemplate;

    public EntitlementLeaseServiceImpl(
            UserMapper userMapper,
            NetworkEntitlementMapper entitlementMapper,
            DurationPurchaseMapper purchaseMapper,
            EntitlementUsageLogMapper usageLogMapper,
            EntitlementLeaseReceiptMapper receiptMapper,
            PlatformTransactionManager transactionManager) {
        this.userMapper = userMapper;
        this.entitlementMapper = entitlementMapper;
        this.purchaseMapper = purchaseMapper;
        this.usageLogMapper = usageLogMapper;
        this.receiptMapper = receiptMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public EntitlementLeaseResult acquireLease(
            String trustedTenantId,
            EntitlementLeaseRequest request) {
        requireRequest(request);
        Long tenantId = resolveTenantId(trustedTenantId, request);
        String fingerprint = fingerprint(request);

        try {
            return transactionTemplate.execute(status ->
                    acquireLeaseInTransaction(
                            tenantId,
                            request,
                            fingerprint));
        } catch (DuplicateKeyException exception) {
            return transactionTemplate.execute(status ->
                    replayConcurrentWinner(
                            tenantId,
                            request.getRequestId(),
                            fingerprint,
                            exception));
        }
    }

    private EntitlementLeaseResult acquireLeaseInTransaction(
            Long tenantId,
            EntitlementLeaseRequest request,
            String fingerprint) {
        EntitlementLeaseReceipt existing =
                receiptMapper.selectByRequest(
                        tenantId,
                        request.getRequestId());
        if (existing != null) {
            return replayStoredResult(existing, fingerprint);
        }

        EntitlementLeaseReceipt receipt =
                pendingReceipt(tenantId, request, fingerprint);
        if (receiptMapper.insert(receipt) != 1) {
            throw new IllegalStateException("权益租约收据写入失败");
        }

        EntitlementLeaseResult result =
                calculateLease(tenantId, request);
        completeReceipt(receipt, result);
        return result;
    }

    private EntitlementLeaseResult calculateLease(
            Long tenantId,
            EntitlementLeaseRequest request) {
        User user = userMapper.selectById(request.getUserId());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            return denied(null, "USER_UNAVAILABLE");
        }

        NetworkEntitlement entitlement =
                entitlementMapper.selectByUserIdForUpdate(
                        tenantId,
                        request.getUserId());
        if (entitlement != null
                && request.getEntitlementId() != null
                && !request.getEntitlementId().equals(
                        entitlement.getEntitlementId())) {
            throw new IllegalArgumentException(
                    "Session 权益标识与租户用户不一致");
        }
        if (entitlement == null
                || !Integer.valueOf(1).equals(entitlement.getStatus())) {
            return denied(entitlement, "ENTITLEMENT_UNAVAILABLE");
        }

        int requestTtl = Math.min(
                request.getRequestedTtlSeconds(),
                MAX_TTL_SECONDS);
        LocalDateTime now = LocalDateTime.now();

        if (EntitlementTradeConstants.MODE_UNLIMITED.equalsIgnoreCase(
                entitlement.getMode())) {
            return allowed(
                    entitlement,
                    requestTtl,
                    0L,
                    entitlement.getRemainingSeconds(),
                    "UNLIMITED_ACTIVE");
        }
        if (SUBSCRIPTION.equalsIgnoreCase(entitlement.getMode())) {
            return handleSubscription(entitlement, requestTtl, now);
        }
        if (!DURATION_MODE.equalsIgnoreCase(entitlement.getMode())) {
            return denied(null, "UNKNOWN_ENTITLEMENT_MODE");
        }

        long before = entitlement.getRemainingSeconds() == null
                ? 0L
                : entitlement.getRemainingSeconds();
        if (before <= 0) {
            return denied(entitlement, "DURATION_EXHAUSTED");
        }
        if (request.getUsageSeconds() == 0) {
            return allowed(
                    entitlement,
                    durationTtl(requestTtl, before),
                    0L,
                    before,
                    "DURATION_AVAILABLE");
        }

        long charged = Math.min(request.getUsageSeconds(), before);
        long after = before - charged;
        List<DurationPurchase> purchases =
                purchaseMapper.selectUsableLotsForUpdate(
                        tenantId,
                        request.getUserId());
        long purchaseTotal = purchases.stream()
                .map(DurationPurchase::getRemainingSeconds)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        if (purchaseTotal != before) {
            throw new IllegalStateException(
                    "购买时长订单余额与汇总余额不一致");
        }
        if (entitlementMapper.deductRemainingSeconds(
                tenantId,
                entitlement.getEntitlementId(),
                charged) != 1) {
            throw new IllegalStateException("购买时长汇总余额扣减失败");
        }

        appendUsageLogs(
                tenantId,
                request,
                entitlement,
                purchases,
                before,
                after,
                charged,
                now);
        Integer ttl = durationTtl(requestTtl, after);
        return ttl == null
                ? deniedAfterCharge(entitlement, charged, after)
                : allowed(
                        entitlement,
                        ttl,
                        charged,
                        after,
                        "DURATION_AVAILABLE");
    }

    private void appendUsageLogs(
            Long tenantId,
            EntitlementLeaseRequest request,
            NetworkEntitlement entitlement,
            List<DurationPurchase> purchases,
            long before,
            long after,
            long charged,
            LocalDateTime now) {
        long unallocated = charged;
        int lineNo = 1;
        for (DurationPurchase purchase : purchases) {
            if (unallocated <= 0) {
                break;
            }

            long part = Math.min(
                    unallocated,
                    purchase.getRemainingSeconds());
            long purchaseAfter =
                    purchase.getRemainingSeconds() - part;
            purchase.setRemainingSeconds(purchaseAfter);
            purchase.setStatus(purchaseAfter == 0 ? 2 : 1);
            purchase.setUpdateTime(now);
            if (purchaseMapper.updateById(purchase) != 1) {
                throw new IllegalStateException(
                        "购买时长批次扣减失败");
            }

            EntitlementUsageLog log = new EntitlementUsageLog();
            log.setTenantId(tenantId);
            log.setEntitlementId(entitlement.getEntitlementId());
            log.setUserId(request.getUserId());
            log.setRequestId(request.getRequestId());
            log.setLineNo(lineNo++);
            log.setPurchaseId(purchase.getPurchaseId());
            log.setAuthorizationMode(DURATION_MODE);
            log.setSessionId(request.getSessionId());
            log.setChangeSeconds(-part);
            log.setBeforeSeconds(before);
            log.setAfterSeconds(after);
            log.setReason("ONLINE_USAGE");
            log.setCreateTime(now);
            if (usageLogMapper.insert(log) != 1) {
                throw new IllegalStateException(
                        "权益使用流水写入失败");
            }
            unallocated -= part;
        }
        if (unallocated != 0) {
            throw new IllegalStateException(
                    "购买时长订单余额与汇总余额不一致");
        }
    }

    private EntitlementLeaseReceipt pendingReceipt(
            Long tenantId,
            EntitlementLeaseRequest request,
            String fingerprint) {
        EntitlementLeaseReceipt receipt =
                new EntitlementLeaseReceipt();
        receipt.setTenantId(tenantId);
        receipt.setRequestId(request.getRequestId());
        receipt.setRequestFingerprint(fingerprint);
        receipt.setEntitlementId(request.getEntitlementId());
        receipt.setUserId(request.getUserId());
        receipt.setSessionId(request.getSessionId());
        receipt.setUsageSeconds(request.getUsageSeconds());
        receipt.setRequestedTtlSeconds(
                request.getRequestedTtlSeconds());
        receipt.setReceiptStatus("PENDING");
        return receipt;
    }

    private void completeReceipt(
            EntitlementLeaseReceipt receipt,
            EntitlementLeaseResult result) {
        receipt.setReceiptStatus("COMPLETED");
        receipt.setResultAllowed(result.getAllowed());
        receipt.setResultEntitlementId(result.getEntitlementId());
        receipt.setResultMode(result.getMode());
        receipt.setResultTtlSeconds(result.getTtlSeconds());
        receipt.setResultChargedSeconds(result.getChargedSeconds());
        receipt.setResultRemainingSeconds(result.getRemainingSeconds());
        receipt.setResultSubscriptionEndTime(
                result.getSubscriptionEndTime());
        receipt.setResultReason(result.getReason());
        receipt.setCompletedTime(LocalDateTime.now());
        if (receiptMapper.updateById(receipt) != 1) {
            throw new IllegalStateException("权益租约收据完成失败");
        }
    }

    private EntitlementLeaseResult replayStoredResult(
            EntitlementLeaseReceipt receipt,
            String fingerprint) {
        if (receipt == null) {
            throw new IllegalStateException("权益租约收据不存在");
        }
        if (!Objects.equals(
                receipt.getRequestFingerprint(),
                fingerprint)) {
            throw ApiStatusException.idempotencyConflict(
                    "requestId 已用于不同的权益租约请求");
        }
        if (!"COMPLETED".equals(receipt.getReceiptStatus())) {
            throw ApiStatusException.serviceUnavailable(
                    "权益租约请求结果尚未完成");
        }

        EntitlementLeaseResult result =
                new EntitlementLeaseResult();
        result.setAllowed(receipt.getResultAllowed());
        result.setDuplicate(true);
        result.setEntitlementId(
                receipt.getResultEntitlementId());
        result.setMode(receipt.getResultMode());
        result.setTtlSeconds(receipt.getResultTtlSeconds());
        result.setChargedSeconds(
                receipt.getResultChargedSeconds());
        result.setRemainingSeconds(
                receipt.getResultRemainingSeconds());
        result.setSubscriptionEndTime(
                receipt.getResultSubscriptionEndTime());
        result.setReason(receipt.getResultReason());
        return result;
    }

    private EntitlementLeaseResult replayConcurrentWinner(
            Long tenantId,
            String requestId,
            String fingerprint,
            DuplicateKeyException duplicateKeyException) {
        EntitlementLeaseReceipt receipt =
                receiptMapper.selectByRequest(tenantId, requestId);
        if (receipt == null) {
            throw duplicateKeyException;
        }
        return replayStoredResult(receipt, fingerprint);
    }

    private Long resolveTenantId(
            String trustedTenantId,
            EntitlementLeaseRequest request) {
        if (StringUtils.hasText(trustedTenantId)) {
            return TenantScopeUtils.requireTenantId(trustedTenantId);
        }
        if (request.getEntitlementId() == null) {
            throw new IllegalArgumentException(
                    "后台租约缺少可信租户或权益标识");
        }

        NetworkEntitlement entitlement =
                entitlementMapper.selectById(
                        request.getEntitlementId());
        if (entitlement == null
                || !Objects.equals(
                        request.getUserId(),
                        entitlement.getUserId())) {
            throw new IllegalArgumentException(
                    "Session 权益标识无效");
        }
        return TenantScopeUtils.requireTenantId(
                entitlement.getTenantId());
    }

    private EntitlementLeaseResult denied(
            NetworkEntitlement entitlement,
            String reason) {
        EntitlementLeaseResult result =
                new EntitlementLeaseResult();
        result.setAllowed(false);
        result.setDuplicate(false);
        result.setTtlSeconds(null);
        result.setChargedSeconds(0L);
        result.setReason(reason);
        if (entitlement != null) {
            result.setEntitlementId(
                    entitlement.getEntitlementId());
            result.setMode(entitlement.getMode());
            result.setRemainingSeconds(
                    entitlement.getRemainingSeconds());
            result.setSubscriptionEndTime(
                    entitlement.getSubscriptionEndTime());
        }
        return result;
    }

    private EntitlementLeaseResult deniedAfterCharge(
            NetworkEntitlement entitlement,
            long charged,
            long remaining) {
        EntitlementLeaseResult result =
                denied(entitlement, "DURATION_EXHAUSTED");
        result.setChargedSeconds(charged);
        result.setRemainingSeconds(remaining);
        return result;
    }

    private EntitlementLeaseResult handleSubscription(
            NetworkEntitlement entitlement,
            int requestTtl,
            LocalDateTime now) {
        LocalDateTime start =
                entitlement.getSubscriptionStartTime();
        LocalDateTime end =
                entitlement.getSubscriptionEndTime();
        if (start == null
                || end == null
                || !start.isBefore(end)) {
            return denied(
                    entitlement,
                    "SUBSCRIPTION_TIME_INVALID");
        }
        if (now.isBefore(start)) {
            return denied(
                    entitlement,
                    "SUBSCRIPTION_NOT_STARTED");
        }
        if (!now.isBefore(end)) {
            return denied(
                    entitlement,
                    "SUBSCRIPTION_EXPIRED");
        }

        long remaining = Duration.between(now, end).getSeconds();
        if (remaining <= 0) {
            return denied(
                    entitlement,
                    "SUBSCRIPTION_EXPIRED");
        }
        int ttlSeconds =
                (int) Math.min((long) requestTtl, remaining);
        return allowed(
                entitlement,
                ttlSeconds,
                0L,
                0L,
                "SUBSCRIPTION_ACTIVE");
    }

    private Integer durationTtl(
            int requestTtl,
            long remainingSeconds) {
        if (remainingSeconds <= 0) {
            return null;
        }
        return (int) Math.min(
                (long) requestTtl,
                remainingSeconds);
    }

    private EntitlementLeaseResult allowed(
            NetworkEntitlement entitlement,
            Integer ttlSeconds,
            long chargedSeconds,
            Long remainingSeconds,
            String reason) {
        EntitlementLeaseResult result =
                new EntitlementLeaseResult();
        result.setAllowed(
                ttlSeconds != null && ttlSeconds > 0);
        result.setDuplicate(false);
        result.setEntitlementId(
                entitlement.getEntitlementId());
        result.setMode(entitlement.getMode());
        result.setTtlSeconds(ttlSeconds);
        result.setChargedSeconds(chargedSeconds);
        result.setRemainingSeconds(remainingSeconds);
        result.setSubscriptionEndTime(
                entitlement.getSubscriptionEndTime());
        result.setReason(reason);
        return result;
    }

    private String fingerprint(
            EntitlementLeaseRequest request) {
        String canonical =
                "entitlementId=" + value(request.getEntitlementId())
                        + "\nuserId=" + value(request.getUserId())
                        + "\nsessionId=" + value(request.getSessionId())
                        + "\nusageSeconds=" + value(request.getUsageSeconds())
                        + "\nrequestedTtlSeconds="
                        + value(request.getRequestedTtlSeconds());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format("%02x", item & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 unavailable",
                    exception);
        }
    }

    private String value(Object value) {
        return value == null ? "<null>" : String.valueOf(value);
    }

    private void requireRequest(EntitlementLeaseRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getRequestId())
                || request.getUserId() == null
                || request.getSessionId() == null
                || request.getUsageSeconds() == null
                || request.getRequestedTtlSeconds() == null) {
            throw new IllegalArgumentException(
                    "权益租约请求字段不完整");
        }
    }
}
