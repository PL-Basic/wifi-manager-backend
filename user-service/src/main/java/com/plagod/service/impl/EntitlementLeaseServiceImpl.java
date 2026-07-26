package com.plagod.service.impl;

import com.plagod.dto.user.EntitlementLeaseRequest;
import com.plagod.entity.DurationPurchase;
import com.plagod.entity.EntitlementUsageLog;
import com.plagod.entity.NetworkEntitlement;
import com.plagod.entity.User;
import com.plagod.mapper.DurationPurchaseMapper;
import com.plagod.mapper.EntitlementUsageLogMapper;
import com.plagod.mapper.NetworkEntitlementMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.service.EntitlementLeaseService;
import com.plagod.vo.user.EntitlementLeaseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class EntitlementLeaseServiceImpl implements EntitlementLeaseService {

    // 订阅式
    private static final String SUBSCRIPTION = "SUBSCRIPTION";
    // 时期式
    private static final String DURATION_MODE = "DURATION";
    private static final int MAX_TTL_SECONDS = 20;

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private NetworkEntitlementMapper entitlementMapper;
    @Autowired
    private DurationPurchaseMapper purchaseMapper;
    @Autowired
    private EntitlementUsageLogMapper usageLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EntitlementLeaseResult acquireLease(EntitlementLeaseRequest request) {
        List<EntitlementUsageLog> existing = usageLogMapper.selectByRequestId(request.getRequestId());
        if (!existing.isEmpty()) {
            return duplicateResult(request, existing);
        }

        User user = userMapper.selectById(request.getUserId());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            return denied(null, "USER_UNAVAILABLE");
        }

        // 所有扣费、退款都必须先锁汇总权益，再锁购买批次
        NetworkEntitlement entitlement = entitlementMapper.selectByUserIdForUpdate(request.getUserId());
        if (entitlement == null || !Integer.valueOf(1).equals(entitlement.getStatus())) {
            return denied(entitlement, "ENTITLEMENT_UNAVAILABLE");
        }

        // 等待行锁期间，另一个线程可能已经完成了同一请求
        existing = usageLogMapper.selectByRequestId(request.getRequestId());
        if (!existing.isEmpty()) {
            return duplicateResult(request, existing);
        }

        int requestTtl = Math.min(request.getRequestedTtlSeconds(), MAX_TTL_SECONDS);
        LocalDateTime now = LocalDateTime.now();

        if (SUBSCRIPTION.equalsIgnoreCase(entitlement.getMode())) {
            return handleSubscription(entitlement, requestTtl, now);
        }
        if (!DURATION_MODE.equalsIgnoreCase(entitlement.getMode())) {
            return denied(entitlement, "UNKNOWN_ENTITLEMENT_MODE");
        }

        long before = entitlement.getRemainingSeconds() == null ? 0L : entitlement.getRemainingSeconds();
        if (before <= 0) {
            return denied(entitlement, "DURATION_EXHAUSTED");
        }

        if (request.getUsageSeconds() == 0){
            return allowed(entitlement, durationTtl(requestTtl, before), 0L, before, false, "DURATION_AVAILABLE");
        }

        long charged = Math.min(request.getUsageSeconds(), before);
        long after = before - charged;
        // 先锁定全部可用购买批次，并验证汇总余额和批次余额完全一致
        List<DurationPurchase> purchases = purchaseMapper.selectUsableLotsForUpdate(request.getUserId());
        // 获取批次余额汇总
        long purchaseTotal = purchases.stream()
                .map(DurationPurchase::getRemainingSeconds)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        if (purchaseTotal != before) {
            throw new IllegalStateException("购买时长订单余额与汇总余额不一致");
        }

        if (entitlementMapper.deductRemainingSeconds(entitlement.getEntitlementId(), charged) != 1) {
            throw new IllegalStateException("购买时长汇总余额扣减失败");
        }

        long unallocated = charged;
        int lineNo = 1;

        for (DurationPurchase purchase : purchases) {
            // 已经分配完毕，不能继续生成零秒流水
            if (unallocated <= 0){
                break;
            }

            long part = Math.min(unallocated, purchase.getRemainingSeconds());
            long purchaseAfter = purchase.getRemainingSeconds() - part;

            purchase.setRemainingSeconds(purchaseAfter);
            purchase.setStatus(purchaseAfter == 0 ? 2 : 1);
            purchase.setUpdateTime(now);

            if (purchaseMapper.updateById(purchase) != 1) {
                throw new IllegalStateException("购买时长批次扣减失败");
            }

            EntitlementUsageLog log = new EntitlementUsageLog();
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
                throw new IllegalStateException("权益使用流水写入失败");
            }

            unallocated -= part;
        }
        if (unallocated != 0){
            // 汇总余额与订单批次余额不一致，整个事务必须回滚
            throw new IllegalStateException("购买时长订单余额与汇总余额不一致");
        }

        Integer ttl = durationTtl(requestTtl, after);
        return allowed(entitlement, ttl, charged, after, false, after > 0 ? "DURATION_AVAILABLE" : "DURATION_EXHAUSTED");
    }

    // 处理已经成功执行过的重复请求。
    // 同一个 requestId 可能因为网络超时被再次调用。该方法不会再次扣费，而是读取第一次请求产生的多条购买批次流水，重新组装第一次的业务结果。
    private EntitlementLeaseResult duplicateResult(EntitlementLeaseRequest request, List<EntitlementUsageLog> existing) {
        EntitlementUsageLog first = existing.get(0);

        for (EntitlementUsageLog log : existing) {
            if (!Objects.equals(request.getUserId(), log.getUserId()) || !Objects.equals(request.getSessionId(), log.getSessionId())) {
                throw new IllegalArgumentException("requestId 已被其他用户或会话使用");
            }

            if (!Objects.equals(first.getEntitlementId(), log.getEntitlementId())
                    || !Objects.equals(first.getAuthorizationMode(), log.getAuthorizationMode())
                    || !Objects.equals(first.getAfterSeconds(), log.getAfterSeconds())) {
                throw new IllegalStateException("同一 requestId 的流水数据不一致");
            }
        }

        // 同一请求可能跨越多个购买批次，因此需要汇总所有扣费明细
        long chargedSeconds = existing.stream()
                .map(EntitlementUsageLog::getChangeSeconds)
                .filter(Objects::nonNull)
                .filter(change -> change < 0)
                .mapToLong(change -> -change)
                .sum();

        long remainingSeconds = first.getAfterSeconds() == null ? 0L : first.getAfterSeconds();
        Integer ttlSeconds = durationTtl(request.getRequestedTtlSeconds(), remainingSeconds);

        EntitlementLeaseResult result = new EntitlementLeaseResult();
        result.setAllowed(ttlSeconds != null);
        result.setDuplicate(true);
        result.setEntitlementId(first.getEntitlementId());
        result.setMode(first.getAuthorizationMode());
        result.setTtlSeconds(ttlSeconds);
        result.setChargedSeconds(chargedSeconds);
        result.setRemainingSeconds(remainingSeconds);
        result.setSubscriptionEndTime(null);
        result.setReason(remainingSeconds > 0 ? "DURATION_AVAILABLE" : "DURATION_EXHAUSTED");
        return result;
    }

    // 统一构造拒绝授权或拒绝续租的结果。
    // 该方法不抛业务异常，因为“余额耗尽、订阅过期、用户停用”都是可以正常返回给调用方的业务结果。
    private EntitlementLeaseResult denied(NetworkEntitlement entitlement, String reason) {
        EntitlementLeaseResult result = new EntitlementLeaseResult();
        result.setAllowed(false);
        result.setDuplicate(false);
        result.setTtlSeconds(null);
        result.setChargedSeconds(0L);
        result.setReason(reason);

        if (entitlement != null) {
            result.setEntitlementId(entitlement.getEntitlementId());
            result.setMode(entitlement.getMode());
            result.setRemainingSeconds(entitlement.getRemainingSeconds());
            result.setSubscriptionEndTime(entitlement.getSubscriptionEndTime());
        }

        return result;
    }

    // 处理订阅权益
    // 订阅模式不扣除 remainingSeconds，只检查当前时间是否位于订阅有效期内。有效区间采用“开始时间包含、结束时间不包含”：
    private EntitlementLeaseResult handleSubscription(NetworkEntitlement entitlement, int requestTtl, LocalDateTime now) {
        LocalDateTime start = entitlement.getSubscriptionStartTime();
        LocalDateTime end = entitlement.getSubscriptionEndTime();

        if (start == null || end == null || !start.isBefore(end)) {
            return denied(entitlement, "SUBSCRIPTION_TIME_INVALID");
        }
        if (now.isBefore(start)) {
            return denied(entitlement, "SUBSCRIPTION_NOT_STARTED");
        }
        if (!now.isBefore(end)) {
            return denied(entitlement, "SUBSCRIPTION_EXPIRED");
        }

        long remaining = Duration.between(now, end).getSeconds();
        if (remaining <= 0) {
            return denied(entitlement, "SUBSCRIPTION_EXPIRED");
        }

        int ttlSeconds = (int) Math.min((long) requestTtl, remaining);
        return allowed(entitlement, ttlSeconds, 0L, 0L, false, "SUBSCRIPTION_ACTIVE");
    }

    // 计算购买时长模式本次能够下发的 TTL。
    // 剩余时长大于 0 时：ttl = min(申请的 TTL, 用户剩余秒数)
    private Integer durationTtl(int requestTtl, long remainingSeconds) {
        if (remainingSeconds <= 0) {
            return null;
        }

        return (int) Math.min((long) requestTtl, remainingSeconds);
    }

    // 统一组装一次权益租约计算结果
    // 该方法只负责创建返回对象，不会操作数据库，也不会发布 MQTT。
    // allowed 根据 ttlSeconds 是否为正数计算：
    // 有正 TTL 表示可以发布 ALLOW；
    // TTL 为 null 表示不允许继续授权。
    private EntitlementLeaseResult allowed(NetworkEntitlement entitlement, Integer ttlSeconds, long chargedSeconds, long remainingSeconds, boolean duplicate,String reason) {
        EntitlementLeaseResult result = new EntitlementLeaseResult();

        result.setAllowed(ttlSeconds != null && ttlSeconds > 0);
        result.setDuplicate(duplicate);
        result.setEntitlementId(entitlement.getEntitlementId());
        result.setMode(entitlement.getMode());
        result.setTtlSeconds(ttlSeconds);
        result.setChargedSeconds(chargedSeconds);
        result.setRemainingSeconds(remainingSeconds);
        result.setSubscriptionEndTime(entitlement.getSubscriptionEndTime());
        result.setReason(reason);

        return result;
    }
}