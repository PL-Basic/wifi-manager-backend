package com.plagod.service.impl;

import com.plagod.audit.AuditDetail;
import com.plagod.audit.AuditTargetId;
import com.plagod.audit.AuditTenantId;
import com.plagod.audit.Audited;
import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.dto.entitlement.EntitlementAdjustmentRequest;
import com.plagod.dto.entitlement.UnlimitedEntitlementRequest;
import com.plagod.exception.ApiStatusException;
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
    @Audited(
            action = "entitlement.adjust",
            targetType = "USER_ENTITLEMENT",
            scope = Audited.Scope.TENANT,
            tenantIdSource = Audited.TenantIdSource.ARGUMENT,
            recordDenied = true,
            recordFailed = true)
    @Transactional(rollbackFor = Exception.class)
    public EntitlementSnapshotVO adjust(
            @AuditTenantId Long tenantId,
            @AuditTargetId Long userId,
            @AuditDetail("operatorId") Long operatorId,
            String operatorName,
            EntitlementAdjustmentRequest request) {

        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("租户身份无效");
        }
        validateRequest(userId, operatorId, operatorName, request);

        String mode = request.getMode().trim().toUpperCase(Locale.ROOT);
        String requestId = "ADJ:" + request.getRequestId().trim();
        long changeSeconds = request.getChangeSeconds();

        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new IllegalArgumentException("目标用户不存在");
        }

        NetworkEntitlement entitlement = entitlementMapper.selectByUserIdForUpdate(tenantId, userId);

        /*
         * 锁定读取会看到等待用户/权益锁期间已经提交的调整流水。
         */
        List<EntitlementUsageLog> existing = usageLogMapper.selectByRequestIdForUpdate(tenantId, requestId);

        if (!existing.isEmpty()) {
            validateDuplicate(existing, userId, mode, changeSeconds);
            return toSnapshot(entitlement);
        }

        LocalDateTime now = LocalDateTime.now();

        requireCompatibleMode(entitlement, mode, changeSeconds, now);

        if (EntitlementTradeConstants.MODE_DURATION.equals(mode)) {
            entitlement = adjustDuration(entitlement, tenantId, userId, requestId, changeSeconds, now);
        } else {
            entitlement = adjustSubscription(entitlement, tenantId, userId, requestId, changeSeconds, now);
        }

        return toSnapshot(entitlement);
    }

    @Override
    @Audited(
            action = "entitlement.unlimited.adjust",
            targetType = "USER_ENTITLEMENT",
            scope = Audited.Scope.TENANT,
            tenantIdSource = Audited.TenantIdSource.ARGUMENT,
            recordDenied = true,
            recordFailed = true)
    @Transactional(rollbackFor = Exception.class)
    public EntitlementSnapshotVO adjustUnlimited(@AuditTenantId Long tenantId,
                                                 @AuditTargetId Long userId,
                                                 @AuditDetail("operatorId") Long operatorId,
                                                 String operatorName,
                                                 @AuditDetail("operatorRole") Integer operatorRole,
                                                 UnlimitedEntitlementRequest request) {

        if (!Integer.valueOf(0).equals(operatorRole)) {
            throw ApiStatusException.forbidden("仅超级管理员可以授予或撤销无限权益");
        }
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("租户身份无效");
        }
        validateUnlimitedRequest(userId, operatorId, operatorName, request);

        String action = request.getAction().trim().toUpperCase(Locale.ROOT);
        String requestId = "UNL:" + request.getRequestId().trim();
        long actionSignal = "GRANT".equals(action) ? 1L : -1L;

        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null || "GRANT".equals(action) && !Integer.valueOf(1).equals(user.getStatus())) {
            throw new IllegalArgumentException("目标用户不存在或不可用");
        }

        NetworkEntitlement entitlement = entitlementMapper.selectByUserIdForUpdate(tenantId, userId);
        List<EntitlementUsageLog> existing =
                usageLogMapper.selectByRequestIdForUpdate(tenantId, requestId);
        if (!existing.isEmpty()) {
            validateDuplicate(existing, userId, EntitlementTradeConstants.MODE_UNLIMITED, actionSignal);
            return toSnapshot(entitlement);
        }

        LocalDateTime now = LocalDateTime.now();
        boolean isNew = entitlement == null;
        boolean activeUnlimited = !isNew
                && EntitlementTradeConstants.MODE_UNLIMITED.equalsIgnoreCase(entitlement.getMode())
                && Integer.valueOf(1).equals(entitlement.getStatus());
        long before = activeUnlimited ? 1L : 0L;

        if ("GRANT".equals(action)) {
            if (!activeUnlimited) {
                if (!isNew
                        && EntitlementTradeConstants.MODE_DURATION.equalsIgnoreCase(entitlement.getMode())
                        && purchaseMapper.selectRefundReservedByUserForUpdate(tenantId, userId) != null) {
                    throw new IllegalArgumentException("存在退款冻结批次，暂时不能授予无限权益");
                }
                if (isNew) {
                    entitlement = new NetworkEntitlement();
                    entitlement.setTenantId(tenantId);
                    entitlement.setUserId(userId);
                    entitlement.setRemainingSeconds(0L);
                    entitlement.setVersion(0);
                    entitlement.setCreateTime(now);
                } else {
                    entitlement.setUnlimitedPreviousMode(entitlement.getMode());
                    entitlement.setUnlimitedPreviousStatus(entitlement.getStatus());
                    increaseVersion(entitlement);
                }
                entitlement.setMode(EntitlementTradeConstants.MODE_UNLIMITED);
                entitlement.setStatus(1);
                entitlement.setUpdateTime(now);
                saveEntitlement(entitlement, isNew);
            }
        } else if (activeUnlimited) {
            increaseVersion(entitlement);
            restoreEntitlementAfterUnlimited(entitlement, now);
            entitlement.setUpdateTime(now);
            saveEntitlement(entitlement, false);
        } else if (isNew) {
            entitlement = new NetworkEntitlement();
            entitlement.setTenantId(tenantId);
            entitlement.setUserId(userId);
            entitlement.setMode(EntitlementTradeConstants.MODE_UNLIMITED);
            entitlement.setRemainingSeconds(0L);
            entitlement.setStatus(0);
            entitlement.setVersion(0);
            entitlement.setCreateTime(now);
            entitlement.setUpdateTime(now);
            saveEntitlement(entitlement, true);
        }

        long after = EntitlementTradeConstants.MODE_UNLIMITED.equalsIgnoreCase(entitlement.getMode())
                && Integer.valueOf(1).equals(entitlement.getStatus()) ? 1L : 0L;
        insertUnlimitedUsageLog(entitlement, requestId, action, actionSignal, before, after, now);
        return toSnapshot(entitlement);
    }

    private NetworkEntitlement adjustDuration(NetworkEntitlement entitlement, Long tenantId, Long userId, String requestId, long changeSeconds, LocalDateTime now) {

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
            entitlement.setTenantId(tenantId);
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
            DurationPurchase purchase = createAdjustmentPurchase(tenantId, userId, changeSeconds, now);

            insertUsageLog(entitlement, requestId, 1, purchase.getPurchaseId(), changeSeconds, before, after, now);
        } else {
            deductDurationLots(entitlement, tenantId, requestId, -changeSeconds, before, after, now);
        }

        return entitlement;
    }

    private NetworkEntitlement adjustSubscription(NetworkEntitlement entitlement, Long tenantId, Long userId, String requestId, long changeSeconds, LocalDateTime now) {
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
            entitlement.setTenantId(tenantId);
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

    private void deductDurationLots(NetworkEntitlement entitlement, Long tenantId, String requestId, long seconds, long before, long after, LocalDateTime now) {

        List<DurationPurchase> purchases = purchaseMapper.selectUsableLotsForUpdate(tenantId, entitlement.getUserId());

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

    private DurationPurchase createAdjustmentPurchase(Long tenantId, Long userId, long seconds, LocalDateTime now) {

        DurationPurchase purchase = new DurationPurchase();
        purchase.setTenantId(tenantId);
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
        log.setTenantId(entitlement.getTenantId());

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
                        entitlement.getTenantId(), entitlement.getUserId()) != null) {
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

        if (EntitlementTradeConstants.MODE_UNLIMITED.equalsIgnoreCase(entitlement.getMode())
                && Integer.valueOf(1).equals(entitlement.getStatus())) {
            throw new IllegalArgumentException("无限权益尚未撤销");
        }
    }

    private void restoreEntitlementAfterUnlimited(NetworkEntitlement entitlement, LocalDateTime now) {
        String previousMode = entitlement.getUnlimitedPreviousMode();
        boolean previouslyActive = Integer.valueOf(1)
                .equals(entitlement.getUnlimitedPreviousStatus());

        if (EntitlementTradeConstants.MODE_DURATION.equalsIgnoreCase(previousMode)) {
            entitlement.setMode(EntitlementTradeConstants.MODE_DURATION);
            entitlement.setStatus(previouslyActive
                    && entitlement.getRemainingSeconds() != null
                    && entitlement.getRemainingSeconds() > 0 ? 1 : 0);
        } else if (EntitlementTradeConstants.MODE_SUBSCRIPTION.equalsIgnoreCase(previousMode)) {
            entitlement.setMode(EntitlementTradeConstants.MODE_SUBSCRIPTION);
            entitlement.setStatus(previouslyActive
                    && entitlement.getSubscriptionEndTime() != null
                    && entitlement.getSubscriptionEndTime().isAfter(now) ? 1 : 0);
        } else {
            entitlement.setStatus(0);
        }
        entitlement.setUnlimitedPreviousMode(null);
        entitlement.setUnlimitedPreviousStatus(null);
    }

    private void insertUnlimitedUsageLog(NetworkEntitlement entitlement,
                                         String requestId,
                                         String action,
                                         long actionSignal,
                                         long before,
                                         long after,
                                         LocalDateTime now) {
        EntitlementUsageLog log = new EntitlementUsageLog();
        log.setTenantId(entitlement.getTenantId());
        log.setEntitlementId(entitlement.getEntitlementId());
        log.setUserId(entitlement.getUserId());
        log.setRequestId(requestId);
        log.setLineNo(1);
        log.setPurchaseId(null);
        log.setAuthorizationMode(EntitlementTradeConstants.MODE_UNLIMITED);
        log.setSessionId(null);
        log.setChangeSeconds(actionSignal);
        log.setBeforeSeconds(before);
        log.setAfterSeconds(after);
        log.setReason("UNLIMITED_" + action);
        log.setCreateTime(now);

        if (usageLogMapper.insert(log) != 1) {
            throw new IllegalStateException("无限权益调整流水写入失败");
        }
    }

    private EntitlementSnapshotVO toSnapshot(NetworkEntitlement entitlement) {

        if (entitlement == null) {
            return null;
        }

        EntitlementSnapshotVO result = new EntitlementSnapshotVO();
        result.setEntitlementId(entitlement.getEntitlementId());
        result.setTenantId(String.valueOf(entitlement.getTenantId()));
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

    private void validateUnlimitedRequest(Long userId,
                                          Long operatorId,
                                          String operatorName,
                                          UnlimitedEntitlementRequest request) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("目标用户无效");
        }
        if (operatorId == null || operatorId <= 0 || !StringUtils.hasText(operatorName)) {
            throw new IllegalArgumentException("超级管理员身份无效");
        }
        if (request == null || !StringUtils.hasText(request.getRequestId())
                || !StringUtils.hasText(request.getAction())
                || !StringUtils.hasText(request.getReason())) {
            throw new IllegalArgumentException("无限权益调整参数无效");
        }
        String action = request.getAction().trim().toUpperCase(Locale.ROOT);
        if (!"GRANT".equals(action) && !"REVOKE".equals(action)) {
            throw new IllegalArgumentException("无限权益操作无效");
        }
    }
}
