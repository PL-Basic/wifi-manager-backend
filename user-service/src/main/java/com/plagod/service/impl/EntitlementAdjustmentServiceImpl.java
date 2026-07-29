package com.plagod.service.impl;

import com.plagod.audit.Audited;
import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.dto.entitlement.EntitlementAdjustmentRequest;
import com.plagod.entity.entitlement.*;
import com.plagod.entity.user.User;
import com.plagod.mapper.*;
import com.plagod.service.EntitlementAdjustmentService;
import com.plagod.vo.user.EntitlementSnapshotVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class EntitlementAdjustmentServiceImpl implements EntitlementAdjustmentService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private NetworkEntitlementMapper entitlementMapper;
    @Autowired
    private DurationPurchaseMapper purchaseMapper;
    @Autowired
    private EntitlementUsageLogMapper usageLogMapper;

    @Override
    @Audited(action = "entitlement.adjust")
    @Transactional(rollbackFor = Exception.class)
    public EntitlementSnapshotVO adjust(Long userId, Long operatorId, String operatorName, EntitlementAdjustmentRequest request) {

        validateRequest(userId, operatorId, operatorName, request);

        String mode = request.getMode().trim().toUpperCase(Locale.ROOT);
        String requestId = "ADJ:" + request.getRequestId().trim();
        long changeSeconds = request.getChangeSeconds();

        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new IllegalArgumentException("目标用户不存在");
        }

        NetworkEntitlement entitlement = entitlementMapper.selectByUserIdForUpdate(userId);

        /*
         * 锁定读取会看到等待用户/权益锁期间已经提交的调整流水。
         */
        List<EntitlementUsageLog> existing = usageLogMapper.selectByRequestIdForUpdate(requestId);

        if (!existing.isEmpty()) {
            validateDuplicate(existing, userId, mode, changeSeconds);
            return toSnapshot(entitlement);
        }

        LocalDateTime now = LocalDateTime.now();

        requireCompatibleMode(entitlement, mode, changeSeconds, now);

        if (EntitlementTradeConstants.MODE_DURATION.equals(mode)) {
            entitlement = adjustDuration(entitlement, userId, requestId, changeSeconds, now);
        } else {
            entitlement = adjustSubscription(entitlement, userId, requestId, changeSeconds, now);
        }

        return toSnapshot(entitlement);
    }

    private NetworkEntitlement adjustDuration(NetworkEntitlement entitlement, Long userId, String requestId, long changeSeconds, LocalDateTime now) {

        boolean isNew = entitlement == null;
        boolean sameMode = !isNew && EntitlementTradeConstants.MODE_DURATION.equalsIgnoreCase(entitlement.getMode());

        long before = sameMode && entitlement.getRemainingSeconds() != null ? entitlement.getRemainingSeconds() : 0L;
        long after = Math.addExact(before, changeSeconds);

        if (after < 0) {
            throw new IllegalArgumentException("调整后购买时长不能为负数");
        }

        if (isNew) {
            if (changeSeconds < 0) {
                throw new IllegalArgumentException("用户尚无可扣减权益");
            }

            entitlement = new NetworkEntitlement();
            entitlement.setUserId(userId);
            entitlement.setVersion(0);
            entitlement.setCreateTime(now);
        } else {
            increaseVersion(entitlement);
        }

        entitlement.setMode(EntitlementTradeConstants.MODE_DURATION);
        entitlement.setSubscriptionStartTime(null);
        entitlement.setSubscriptionEndTime(null);
        entitlement.setRemainingSeconds(after);
        entitlement.setStatus(1);
        entitlement.setUpdateTime(now);

        saveEntitlement(entitlement, isNew);

        if (changeSeconds > 0) {
            DurationPurchase purchase = createAdjustmentPurchase(userId, changeSeconds, now);

            insertUsageLog(entitlement, requestId, 1, purchase.getPurchaseId(), changeSeconds, before, after, now);
        } else {
            deductDurationLots(entitlement, requestId, -changeSeconds, before, after, now);
        }

        return entitlement;
    }

    private NetworkEntitlement adjustSubscription(NetworkEntitlement entitlement, Long userId, String requestId, long changeSeconds, LocalDateTime now) {
        boolean isNew = entitlement == null;
        boolean sameMode = !isNew && EntitlementTradeConstants.MODE_SUBSCRIPTION.equalsIgnoreCase(entitlement.getMode());

        LocalDateTime oldEnd = sameMode ? entitlement.getSubscriptionEndTime() : null;

        long before = oldEnd != null && oldEnd.isAfter(now) ? Duration.between(now, oldEnd).getSeconds() : 0L;

        long after = Math.addExact(before, changeSeconds);
        if (after < 0) {
            throw new IllegalArgumentException("调整后订阅时长不能为负数");
        }

        if (isNew) {
            if (changeSeconds < 0) {
                throw new IllegalArgumentException("用户尚无可扣减订阅");
            }

            entitlement = new NetworkEntitlement();
            entitlement.setUserId(userId);
            entitlement.setVersion(0);
            entitlement.setCreateTime(now);
        } else {
            increaseVersion(entitlement);
        }

        entitlement.setMode(EntitlementTradeConstants.MODE_SUBSCRIPTION);
        entitlement.setRemainingSeconds(0L);
        entitlement.setStatus(1);

        if (!sameMode || entitlement.getSubscriptionStartTime() == null || before == 0) {
            entitlement.setSubscriptionStartTime(now);
        }

        entitlement.setSubscriptionEndTime(now.plusSeconds(after));
        entitlement.setUpdateTime(now);

        saveEntitlement(entitlement, isNew);

        insertUsageLog(entitlement, requestId, 1, null, changeSeconds, before, after, now);

        return entitlement;
    }

    private void deductDurationLots(NetworkEntitlement entitlement, String requestId, long seconds, long before, long after, LocalDateTime now) {

        List<DurationPurchase> purchases = purchaseMapper.selectUsableLotsForUpdate(entitlement.getUserId());

        long total = purchases.stream()
                .map(DurationPurchase::getRemainingSeconds)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        if (total != before) {
            throw new IllegalStateException("购买批次余额与汇总权益不一致");
        }

        long unallocated = seconds;
        int lineNo = 1;

        for (DurationPurchase purchase : purchases) {
            if (unallocated <= 0) {
                break;
            }

            long purchaseBefore = purchase.getRemainingSeconds();
            long part = Math.min(unallocated, purchaseBefore);
            long purchaseAfter = purchaseBefore - part;

            purchase.setRemainingSeconds(purchaseAfter);
            purchase.setStatus(purchaseAfter == 0 ? EntitlementTradeConstants.PURCHASE_EXHAUSTED : EntitlementTradeConstants.PURCHASE_USABLE);
            purchase.setUpdateTime(now);

            if (purchaseMapper.updateById(purchase) != 1) {
                throw new IllegalStateException("购买批次调整失败");
            }

            insertUsageLog(entitlement, requestId, lineNo++, purchase.getPurchaseId(), -part, before, after, now);

            unallocated -= part;
        }

        if (unallocated != 0) {
            throw new IllegalStateException("购买批次不足以完成权益调整");
        }
    }

    private DurationPurchase createAdjustmentPurchase(Long userId, long seconds, LocalDateTime now) {

        DurationPurchase purchase = new DurationPurchase();
        purchase.setOrderNo("ADJ" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        purchase.setUserId(userId);
        purchase.setPurchasedSeconds(seconds);
        purchase.setRemainingSeconds(seconds);
        purchase.setPaidAmountCents(0L);
        purchase.setRefundable(0);
        purchase.setStatus(EntitlementTradeConstants.PURCHASE_USABLE);
        purchase.setCreateTime(now);
        purchase.setUpdateTime(now);

        if (purchaseMapper.insert(purchase) != 1) {
            throw new IllegalStateException("管理员调整批次创建失败");
        }
        return purchase;
    }

    private void insertUsageLog(NetworkEntitlement entitlement, String requestId, int lineNo, Long purchaseId, long changeSeconds, long before, long after, LocalDateTime now) {

        EntitlementUsageLog log = new EntitlementUsageLog();

        log.setEntitlementId(entitlement.getEntitlementId());
        log.setUserId(entitlement.getUserId());
        log.setRequestId(requestId);
        log.setLineNo(lineNo);
        log.setPurchaseId(purchaseId);
        log.setAuthorizationMode(entitlement.getMode());
        log.setSessionId(null);
        log.setChangeSeconds(changeSeconds);
        log.setBeforeSeconds(before);
        log.setAfterSeconds(after);
        log.setReason("ADMIN_ADJUSTMENT");
        log.setCreateTime(now);

        if (usageLogMapper.insert(log) != 1) {
            throw new IllegalStateException("权益调整流水写入失败");
        }
    }

    private void validateDuplicate(List<EntitlementUsageLog> logs, Long userId, String mode, long changeSeconds) {

        long storedChange = 0L;

        for (EntitlementUsageLog log : logs) {
            if (!Objects.equals(userId, log.getUserId()) || !mode.equalsIgnoreCase(log.getAuthorizationMode())) {
                throw new IllegalArgumentException("调整请求号已被其他业务使用");
            }
            storedChange = Math.addExact(storedChange, log.getChangeSeconds());
        }

        if (storedChange != changeSeconds) {
            throw new IllegalArgumentException("重复调整请求的变更秒数不一致");
        }
    }

    private void requireCompatibleMode(NetworkEntitlement entitlement, String targetMode, long changeSeconds, LocalDateTime now) {
        if (entitlement == null || targetMode.equalsIgnoreCase(entitlement.getMode())) {
            return;
        }

        if (EntitlementTradeConstants.MODE_DURATION
                .equalsIgnoreCase(entitlement.getMode())
                && purchaseMapper
                .selectRefundReservedByUserForUpdate(
                        entitlement.getUserId()) != null) {
            throw new IllegalArgumentException(
                    "存在退款冻结批次，暂时不能切换权益模式");
        }

        if (changeSeconds < 0) {
            throw new IllegalArgumentException("不能使用其他模式扣减当前权益");
        }

        if (EntitlementTradeConstants.MODE_DURATION.equalsIgnoreCase(entitlement.getMode()) && entitlement.getRemainingSeconds() != null && entitlement.getRemainingSeconds() > 0) {
            throw new IllegalArgumentException("原购买时长尚未用完");
        }

        if (EntitlementTradeConstants.MODE_SUBSCRIPTION.equalsIgnoreCase(entitlement.getMode()) && entitlement.getSubscriptionEndTime() != null && entitlement.getSubscriptionEndTime().isAfter(now)) {
            throw new IllegalArgumentException("原订阅尚未到期");
        }
    }

    private EntitlementSnapshotVO toSnapshot(NetworkEntitlement entitlement) {

        if (entitlement == null) {
            return null;
        }

        EntitlementSnapshotVO result = new EntitlementSnapshotVO();
        result.setEntitlementId(entitlement.getEntitlementId());
        result.setUserId(entitlement.getUserId());
        result.setMode(entitlement.getMode());
        result.setSubscriptionStartTime(entitlement.getSubscriptionStartTime());
        result.setSubscriptionEndTime(entitlement.getSubscriptionEndTime());
        result.setRemainingSeconds(entitlement.getRemainingSeconds());
        result.setStatus(entitlement.getStatus());
        return result;
    }

    private void saveEntitlement(NetworkEntitlement entitlement, boolean isNew) {

        int changed = isNew ? entitlementMapper.insert(entitlement) : entitlementMapper.updateById(entitlement);

        if (changed != 1) {
            throw new IllegalStateException("权益更新失败");
        }
    }

    private void increaseVersion(
            NetworkEntitlement entitlement) {
        entitlement.setVersion(entitlement.getVersion() == null ? 1 : entitlement.getVersion() + 1);
    }

    private void validateRequest(Long userId, Long operatorId, String operatorName, EntitlementAdjustmentRequest request) {

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("目标用户无效");
        }
        if (operatorId == null || operatorId <= 0 || !StringUtils.hasText(operatorName)) {
            throw new IllegalArgumentException("管理员身份无效");
        }
        if (request == null || request.getChangeSeconds() == null || request.getChangeSeconds() == 0) {
            throw new IllegalArgumentException("权益调整秒数不能为0");
        }

        String mode = request.getMode() == null ? "" : request.getMode().trim().toUpperCase(Locale.ROOT);
        if (!EntitlementTradeConstants.MODE_DURATION.equals(mode) && !EntitlementTradeConstants.MODE_SUBSCRIPTION.equals(mode)) {
            throw new IllegalArgumentException("权益模式无效");
        }
    }
}