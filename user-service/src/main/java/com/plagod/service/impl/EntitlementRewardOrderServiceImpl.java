package com.plagod.service.impl;

import com.plagod.audit.Audited;
import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.dto.entitlement.EntitlementRewardOrderRequest;
import com.plagod.entity.entitlement.DurationPurchase;
import com.plagod.entity.entitlement.EntitlementOrder;
import com.plagod.entity.entitlement.EntitlementUsageLog;
import com.plagod.entity.entitlement.NetworkEntitlement;
import com.plagod.entity.entitlement.TradeStatusLog;
import com.plagod.entity.user.User;
import com.plagod.mapper.DurationPurchaseMapper;
import com.plagod.mapper.EntitlementOrderMapper;
import com.plagod.mapper.EntitlementUsageLogMapper;
import com.plagod.mapper.NetworkEntitlementMapper;
import com.plagod.mapper.TradeStatusLogMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.service.EntitlementRewardOrderService;
import com.plagod.vo.entitlement.EntitlementOrderVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class EntitlementRewardOrderServiceImpl implements EntitlementRewardOrderService {

    private static final DateTimeFormatter ORDER_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String PRODUCT_ADMIN_REWARD = "ADMIN_REWARD";
    private static final String ORDER_TYPE_REWARD = "REWARD";
    private static final Set<Integer> ALLOWED_SUBSCRIPTION_MONTHS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(1, 3, 6, 12)));

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private EntitlementOrderMapper orderMapper;
    @Autowired
    private NetworkEntitlementMapper entitlementMapper;
    @Autowired
    private DurationPurchaseMapper purchaseMapper;
    @Autowired
    private EntitlementUsageLogMapper usageLogMapper;
    @Autowired
    private TradeStatusLogMapper statusLogMapper;

    @Override
    @Audited(
            action = "entitlement.reward-order.create",
            scope = Audited.Scope.TENANT,
            tenantIdSource = Audited.TenantIdSource.REQUEST)
    @Transactional(rollbackFor = Exception.class)
    public EntitlementOrderVO create(Long tenantId,
                                     Long userId,
                                     Long operatorId,
                                     String operatorName,
                                     EntitlementRewardOrderRequest request) {

        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("租户身份无效");
        }
        validateRequest(userId, operatorId, operatorName, request);

        String mode = request.getMode().trim().toUpperCase(Locale.ROOT);
        String clientRequestId = "REWARD:" + request.getRequestId().trim();
        String reason = request.getReason().trim();
        LocalDateTime now = LocalDateTime.now();

        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new IllegalArgumentException("目标用户不存在或不可用");
        }

        EntitlementOrder candidate = new EntitlementOrder();
        candidate.setTenantId(tenantId);
        candidate.setOrderNo(generateOrderNo(now));
        candidate.setUserId(userId);
        candidate.setClientRequestId(clientRequestId);
        candidate.setProductCode(PRODUCT_ADMIN_REWARD);
        candidate.setOrderType(ORDER_TYPE_REWARD);
        candidate.setEntitlementMode(mode);
        candidate.setGrantSeconds(request.getGrantSeconds());
        candidate.setGrantMonths(request.getGrantMonths());
        candidate.setAmountCents(request.getAmountCents());
        candidate.setPaidAmountCents(0L);
        candidate.setRefundedAmountCents(0L);
        candidate.setStatus(EntitlementTradeConstants.ORDER_FULFILLED);
        candidate.setExpireTime(now);
        candidate.setFulfilledTime(now);
        candidate.setRemark(reason);
        candidate.setVersion(0);
        candidate.setCreateTime(now);
        candidate.setUpdateTime(now);

        orderMapper.insertOrResolveExisting(candidate);

        EntitlementOrder stored = orderMapper.selectByUserRequestForUpdate(tenantId, userId, clientRequestId);
        if (stored == null) {
            throw new IllegalStateException("奖励订单创建结果无法确认");
        }

        validateDuplicate(stored, candidate);

        // 随机订单号相同表示本事务刚插入；重复请求直接返回首次结果，不能重复发放。
        if (!candidate.getOrderNo().equals(stored.getOrderNo())) {
            return toOrderVO(stored);
        }

        NetworkEntitlement entitlement = entitlementMapper.selectByUserIdForUpdate(tenantId, userId);
        requireCompatibleMode(entitlement, mode, now);

        if (EntitlementTradeConstants.MODE_DURATION.equals(mode)) {
            grantDuration(stored, entitlement, now);
        } else {
            grantSubscription(stored, entitlement, now);
        }

        appendStatusLog(stored, operatorId, reason, now);
        return toOrderVO(stored);
    }

    private void grantDuration(EntitlementOrder order,
                               NetworkEntitlement entitlement,
                               LocalDateTime now) {

        boolean isNew = entitlement == null;
        boolean sameMode = !isNew && EntitlementTradeConstants.MODE_DURATION.equalsIgnoreCase(entitlement.getMode());
        long before = sameMode && entitlement.getRemainingSeconds() != null ? entitlement.getRemainingSeconds() : 0L;
        long after = Math.addExact(before, order.getGrantSeconds());

        if (isNew) {
            entitlement = new NetworkEntitlement();
            entitlement.setTenantId(order.getTenantId());
            entitlement.setUserId(order.getUserId());
            entitlement.setVersion(0);
            entitlement.setCreateTime(now);
        } else {
            increaseVersion(entitlement);
        }

        entitlement.setMode(EntitlementTradeConstants.MODE_DURATION);
        entitlement.setRemainingSeconds(after);
        entitlement.setSubscriptionStartTime(null);
        entitlement.setSubscriptionEndTime(null);
        entitlement.setStatus(1);
        entitlement.setUpdateTime(now);
        saveEntitlement(entitlement, isNew);

        DurationPurchase purchase = new DurationPurchase();
        purchase.setTenantId(order.getTenantId());
        purchase.setOrderNo(order.getOrderNo());
        purchase.setUserId(order.getUserId());
        purchase.setPurchasedSeconds(order.getGrantSeconds());
        purchase.setRemainingSeconds(order.getGrantSeconds());
        purchase.setPaidAmountCents(0L);
        purchase.setRefundable(0);
        purchase.setStatus(EntitlementTradeConstants.PURCHASE_USABLE);
        purchase.setCreateTime(now);
        purchase.setUpdateTime(now);

        if (purchaseMapper.insert(purchase) != 1) {
            throw new IllegalStateException("奖励购买批次创建失败");
        }

        insertUsageLog(order, entitlement, purchase.getPurchaseId(),
                order.getGrantSeconds(), before, after, now);
    }

    private void grantSubscription(EntitlementOrder order,
                                   NetworkEntitlement entitlement,
                                   LocalDateTime now) {

        boolean isNew = entitlement == null;
        boolean sameMode = !isNew && EntitlementTradeConstants.MODE_SUBSCRIPTION.equalsIgnoreCase(entitlement.getMode());
        LocalDateTime oldEnd = sameMode ? entitlement.getSubscriptionEndTime() : null;
        long before = oldEnd != null && oldEnd.isAfter(now) ? Duration.between(now, oldEnd).getSeconds() : 0L;
        LocalDateTime baseTime = oldEnd != null && oldEnd.isAfter(now) ? oldEnd : now;
        if (order.getGrantMonths() == null
                || !ALLOWED_SUBSCRIPTION_MONTHS.contains(order.getGrantMonths())) {
            throw new IllegalStateException("奖励订阅缺少有效自然月快照");
        }
        LocalDateTime endTime = baseTime.plusMonths(order.getGrantMonths());
        long after = Duration.between(now, endTime).getSeconds();

        if (isNew) {
            entitlement = new NetworkEntitlement();
            entitlement.setTenantId(order.getTenantId());
            entitlement.setUserId(order.getUserId());
            entitlement.setVersion(0);
            entitlement.setCreateTime(now);
        } else {
            increaseVersion(entitlement);
        }

        entitlement.setMode(EntitlementTradeConstants.MODE_SUBSCRIPTION);
        entitlement.setRemainingSeconds(0L);
        entitlement.setStatus(1);

        if (!sameMode || oldEnd == null || !oldEnd.isAfter(now)) {
            entitlement.setSubscriptionStartTime(now);
        }

        entitlement.setSubscriptionEndTime(endTime);
        entitlement.setUpdateTime(now);
        saveEntitlement(entitlement, isNew);
        insertUsageLog(order, entitlement, null, after - before, before, after, now);
    }

    private void insertUsageLog(EntitlementOrder order,
                                NetworkEntitlement entitlement,
                                Long purchaseId,
                                long changeSeconds,
                                long before,
                                long after,
                                LocalDateTime now) {

        EntitlementUsageLog usageLog = new EntitlementUsageLog();
        usageLog.setTenantId(order.getTenantId());
        usageLog.setEntitlementId(entitlement.getEntitlementId());
        usageLog.setUserId(order.getUserId());
        usageLog.setRequestId(order.getClientRequestId());
        usageLog.setLineNo(1);
        usageLog.setPurchaseId(purchaseId);
        usageLog.setAuthorizationMode(order.getEntitlementMode());
        usageLog.setSessionId(null);
        usageLog.setChangeSeconds(changeSeconds);
        usageLog.setBeforeSeconds(before);
        usageLog.setAfterSeconds(after);
        usageLog.setReason("ADMIN_REWARD");
        usageLog.setCreateTime(now);

        if (usageLogMapper.insert(usageLog) != 1) {
            throw new IllegalStateException("奖励权益流水写入失败");
        }
    }

    private void requireCompatibleMode(NetworkEntitlement entitlement,
                                       String targetMode,
                                       LocalDateTime now) {

        if (entitlement == null || targetMode.equalsIgnoreCase(entitlement.getMode())) {
            return;
        }

        if (EntitlementTradeConstants.MODE_DURATION.equalsIgnoreCase(entitlement.getMode())
                && purchaseMapper.selectRefundReservedByUserForUpdate(
                entitlement.getTenantId(), entitlement.getUserId()) != null) {
            throw new IllegalArgumentException("存在退款冻结批次，暂时不能切换权益模式");
        }

        if (EntitlementTradeConstants.MODE_DURATION.equalsIgnoreCase(entitlement.getMode())
                && entitlement.getRemainingSeconds() != null
                && entitlement.getRemainingSeconds() > 0) {
            throw new IllegalArgumentException("原购买时长尚未用完，不能改为订阅奖励");
        }

        if (EntitlementTradeConstants.MODE_SUBSCRIPTION.equalsIgnoreCase(entitlement.getMode())
                && entitlement.getSubscriptionEndTime() != null
                && entitlement.getSubscriptionEndTime().isAfter(now)) {
            throw new IllegalArgumentException("原订阅尚未到期，不能改为时长奖励");
        }

        if (EntitlementTradeConstants.MODE_UNLIMITED.equalsIgnoreCase(entitlement.getMode())
                && Integer.valueOf(1).equals(entitlement.getStatus())) {
            throw new IllegalArgumentException("无限权益尚未撤销");
        }
    }

    private void saveEntitlement(NetworkEntitlement entitlement, boolean isNew) {
        int changed = isNew ? entitlementMapper.insert(entitlement) : entitlementMapper.updateById(entitlement);
        if (changed != 1) {
            throw new IllegalStateException("奖励权益更新失败");
        }
    }

    private void appendStatusLog(EntitlementOrder order,
                                 Long operatorId,
                                 String reason,
                                 LocalDateTime now) {

        TradeStatusLog log = new TradeStatusLog();
        log.setTenantId(order.getTenantId());
        log.setBusinessType(EntitlementTradeConstants.BUSINESS_ORDER);
        log.setBusinessNo(order.getOrderNo());
        log.setEventKey("REWARD:" + order.getClientRequestId());
        log.setFromStatus(null);
        log.setToStatus(EntitlementTradeConstants.ORDER_FULFILLED);
        log.setOperatorType(EntitlementTradeConstants.OPERATOR_ADMIN);
        log.setOperatorId(operatorId);
        log.setRemark(reason);
        log.setCreateTime(now);
        statusLogMapper.insertIgnore(log);
    }

    private void validateDuplicate(EntitlementOrder stored, EntitlementOrder candidate) {
        if (!PRODUCT_ADMIN_REWARD.equals(stored.getProductCode())
                || !ORDER_TYPE_REWARD.equals(stored.getOrderType())
                || !Objects.equals(stored.getEntitlementMode(), candidate.getEntitlementMode())
                || !Objects.equals(stored.getGrantSeconds(), candidate.getGrantSeconds())
                || !Objects.equals(stored.getGrantMonths(), candidate.getGrantMonths())
                || !Objects.equals(stored.getAmountCents(), candidate.getAmountCents())
                || !Objects.equals(stored.getRemark(), candidate.getRemark())) {
            throw new IllegalArgumentException("奖励请求号已被其他订单参数使用");
        }
    }

    private void validateRequest(Long userId,
                                 Long operatorId,
                                 String operatorName,
                                 EntitlementRewardOrderRequest request) {

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("目标用户无效");
        }
        if (operatorId == null || operatorId <= 0 || !StringUtils.hasText(operatorName)) {
            throw new IllegalArgumentException("超级管理员身份无效");
        }
        if (request == null || request.getAmountCents() == null
                || request.getAmountCents() < 0) {
            throw new IllegalArgumentException("奖励订单参数无效");
        }

        String mode = request.getMode() == null ? "" : request.getMode().trim().toUpperCase(Locale.ROOT);
        if (!EntitlementTradeConstants.MODE_DURATION.equals(mode)
                && !EntitlementTradeConstants.MODE_SUBSCRIPTION.equals(mode)) {
            throw new IllegalArgumentException("奖励权益模式无效");
        }
        if (EntitlementTradeConstants.MODE_DURATION.equals(mode)
                && (request.getGrantSeconds() == null || request.getGrantSeconds() <= 0
                || request.getGrantMonths() != null)) {
            throw new IllegalArgumentException("时长奖励参数无效");
        }
        if (EntitlementTradeConstants.MODE_SUBSCRIPTION.equals(mode)
                && (!ALLOWED_SUBSCRIPTION_MONTHS.contains(request.getGrantMonths())
                || request.getGrantSeconds() != null && request.getGrantSeconds() != 0)) {
            throw new IllegalArgumentException("订阅奖励自然月只能为1、3、6或12");
        }
    }

    private String generateOrderNo(LocalDateTime now) {
        String randomPart = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 20).toUpperCase(Locale.ROOT);
        return "RWD" + ORDER_TIME_FORMAT.format(now) + randomPart;
    }

    private void increaseVersion(NetworkEntitlement entitlement) {
        entitlement.setVersion(entitlement.getVersion() == null ? 1 : entitlement.getVersion() + 1);
    }

    private EntitlementOrderVO toOrderVO(EntitlementOrder order) {
        EntitlementOrderVO vo = new EntitlementOrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setTenantId(String.valueOf(order.getTenantId()));
        vo.setUserId(order.getUserId());
        vo.setProductCode(order.getProductCode());
        vo.setOrderType(order.getOrderType());
        vo.setEntitlementMode(order.getEntitlementMode());
        vo.setPricingVersion(order.getPricingVersion());
        vo.setGrantSeconds(order.getGrantSeconds());
        vo.setGrantMonths(order.getGrantMonths());
        vo.setAmountCents(order.getAmountCents());
        vo.setReferenceAmountCents(order.getReferenceAmountCents());
        vo.setSubscriptionRatioBps(order.getSubscriptionRatioBps());
        vo.setPeriodDiscountBps(order.getPeriodDiscountBps());
        vo.setPaidAmountCents(order.getPaidAmountCents());
        vo.setRefundedAmountCents(order.getRefundedAmountCents());
        vo.setStatus(order.getStatus());
        vo.setExpireTime(order.getExpireTime());
        vo.setPaidTime(order.getPaidTime());
        vo.setFulfilledTime(order.getFulfilledTime());
        vo.setCloseTime(order.getCloseTime());
        vo.setCloseReason(order.getCloseReason());
        vo.setRemark(order.getRemark());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }
}
