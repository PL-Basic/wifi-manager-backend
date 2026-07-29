package com.plagod.service.impl;

import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.dto.entitlement.VerifiedPaymentCallback;
import com.plagod.entity.entitlement.*;
import com.plagod.entity.user.User;
import com.plagod.mapper.*;
import com.plagod.service.PaymentCallbackService;
import com.plagod.vo.entitlement.PaymentCallbackResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class PaymentCallbackServiceImpl
        implements PaymentCallbackService {

    @Autowired
    private EntitlementOrderMapper orderMapper;
    @Autowired
    private PaymentRecordMapper paymentMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private NetworkEntitlementMapper entitlementMapper;
    @Autowired
    private DurationPurchaseMapper purchaseMapper;
    @Autowired
    private EntitlementUsageLogMapper usageLogMapper;
    @Autowired
    private TradeStatusLogMapper statusLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentCallbackResultVO handleSuccess(VerifiedPaymentCallback callback) {

        validateCallback(callback);
        PaymentRecord located = paymentMapper.selectByBusinessKey(callback.getBusinessKey());

        if (located == null) {
            throw new IllegalArgumentException("支付业务键不存在");
        }

        EntitlementOrder order = orderMapper.selectByOrderNoForUpdate(located.getOrderNo());

        if (order == null) {
            throw new IllegalStateException("支付关联订单不存在");
        }

        PaymentRecord payment = paymentMapper.selectByPaymentNoForUpdate(located.getPaymentNo());

        if (payment == null) {
            throw new IllegalStateException("支付记录不存在");
        }

        validateAssociation(payment, order, callback);
        validateExternalIdentifiers(payment, callback);

        if (EntitlementTradeConstants.PAYMENT_SUCCEEDED.equals(payment.getStatus())) {

            validateRepeatedCallback(payment, callback);

            userMapper.selectByIdForUpdate(payment.getUserId());
            NetworkEntitlement entitlement = entitlementMapper.selectByUserIdForUpdate(payment.getUserId());

            return buildResult(payment, order, entitlement, true);
        }

        if (!EntitlementTradeConstants.PAYMENT_CREATED.equals(payment.getStatus())) {
            throw new IllegalArgumentException("当前支付状态不能处理成功回调");
        }

        if (!EntitlementTradeConstants.ORDER_PENDING_PAYMENT.equals(order.getStatus())) {
            throw new IllegalArgumentException("当前订单状态不能完成支付");
        }

        LocalDateTime now = LocalDateTime.now();

        if (order.getExpireTime() == null || !order.getExpireTime().isAfter(now)) {
            throw new IllegalArgumentException("订单已经过期，不能完成支付");
        }

        User user = userMapper.selectByIdForUpdate(payment.getUserId());

        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new IllegalArgumentException("用户不存在或不可用");
        }

        NetworkEntitlement entitlement = entitlementMapper.selectByUserIdForUpdate(payment.getUserId());

        entitlement = grantEntitlement(order, payment, entitlement, now);

        markPaymentSucceeded(payment, callback, now);
        markOrderPaidAndFulfilled(order, payment, now);

        return buildResult(payment, order, entitlement, false);
    }

    private NetworkEntitlement grantEntitlement(EntitlementOrder order, PaymentRecord payment, NetworkEntitlement entitlement, LocalDateTime now) {

        String targetMode = order.getEntitlementMode();
        requireCompatibleMode(entitlement, targetMode, now);

        boolean isNew = entitlement == null;
        boolean sameMode = !isNew && targetMode.equalsIgnoreCase(entitlement.getMode());

        if (isNew) {
            entitlement = new NetworkEntitlement();
            entitlement.setUserId(order.getUserId());
            entitlement.setVersion(0);
            entitlement.setCreateTime(now);
        } else {
            entitlement.setVersion(entitlement.getVersion() == null ? 1 : entitlement.getVersion() + 1);
        }

        entitlement.setMode(targetMode);
        entitlement.setStatus(1);
        entitlement.setUpdateTime(now);

        long beforeSeconds;
        long afterSeconds;
        Long purchaseId = null;

        if (EntitlementTradeConstants.MODE_DURATION.equals(targetMode)) {

            beforeSeconds = sameMode && entitlement.getRemainingSeconds() != null ? entitlement.getRemainingSeconds() : 0L;

            afterSeconds = Math.addExact(beforeSeconds, order.getGrantSeconds());

            entitlement.setRemainingSeconds(afterSeconds);
            entitlement.setSubscriptionStartTime(null);
            entitlement.setSubscriptionEndTime(null);

            saveEntitlement(entitlement, isNew);

            DurationPurchase purchase = new DurationPurchase();
            purchase.setOrderNo(order.getOrderNo());
            purchase.setUserId(order.getUserId());
            purchase.setPurchasedSeconds(order.getGrantSeconds());
            purchase.setRemainingSeconds(order.getGrantSeconds());
            purchase.setPaidAmountCents(callbackPaidAmount(payment));
            purchase.setRefundable(1);
            purchase.setStatus(EntitlementTradeConstants.PURCHASE_USABLE);
            purchase.setCreateTime(now);
            purchase.setUpdateTime(now);

            if (purchaseMapper.insert(purchase) != 1) {
                throw new IllegalStateException("购买时长批次创建失败");
            }

            purchaseId = purchase.getPurchaseId();
        } else if (EntitlementTradeConstants.MODE_SUBSCRIPTION.equals(targetMode)) {

            LocalDateTime existingEnd = sameMode ? entitlement.getSubscriptionEndTime() : null;

            LocalDateTime baseTime = existingEnd != null && existingEnd.isAfter(now) ? existingEnd : now;

            beforeSeconds = existingEnd != null && existingEnd.isAfter(now) ? Duration.between(now, existingEnd).getSeconds() : 0L;

            LocalDateTime endTime = baseTime.plusSeconds(order.getGrantSeconds());

            afterSeconds = Duration.between(now, endTime).getSeconds();

            if (!sameMode || entitlement.getSubscriptionStartTime() == null || existingEnd == null || !existingEnd.isAfter(now)) {
                entitlement.setSubscriptionStartTime(now);
            }

            entitlement.setSubscriptionEndTime(endTime);
            entitlement.setRemainingSeconds(0L);

            saveEntitlement(entitlement, isNew);
        } else {
            throw new IllegalStateException("订单权益模式无效");
        }

        EntitlementUsageLog usageLog = new EntitlementUsageLog();

        usageLog.setEntitlementId(entitlement.getEntitlementId());
        usageLog.setUserId(order.getUserId());
        usageLog.setRequestId("PAY:" + payment.getPaymentNo());
        usageLog.setLineNo(1);
        usageLog.setPurchaseId(purchaseId);
        usageLog.setAuthorizationMode(targetMode);
        usageLog.setSessionId(null);
        usageLog.setChangeSeconds(order.getGrantSeconds());
        usageLog.setBeforeSeconds(beforeSeconds);
        usageLog.setAfterSeconds(afterSeconds);
        usageLog.setReason("PAYMENT_GRANT");
        usageLog.setCreateTime(now);

        if (usageLogMapper.insert(usageLog) != 1) {
            throw new IllegalStateException("权益发放流水写入失败");
        }

        return entitlement;
    }

    private void saveEntitlement(NetworkEntitlement entitlement, boolean isNew) {

        int changed = isNew ? entitlementMapper.insert(entitlement) : entitlementMapper.updateById(entitlement);

        if (changed != 1) {
            throw new IllegalStateException("权益更新失败");
        }
    }

    private void markPaymentSucceeded(PaymentRecord payment, VerifiedPaymentCallback callback, LocalDateTime now) {

        String previousStatus = payment.getStatus();

        payment.setStatus(EntitlementTradeConstants.PAYMENT_SUCCEEDED);
        payment.setPaidAmountCents(callback.getPaidAmountCents());
        payment.setChannelTransactionNo(callback.getChannelTransactionNo());
        payment.setCallbackEventId(callback.getEventId());
        payment.setCallbackPayloadHash(callback.getPayloadHash());
        payment.setPaidTime(now);
        payment.setFailureCode(null);
        payment.setFailureMessage(null);
        payment.setVersion(payment.getVersion() == null ? 1 : payment.getVersion() + 1);
        payment.setUpdateTime(now);

        if (paymentMapper.updateById(payment) != 1) {
            throw new IllegalStateException("支付状态更新失败");
        }

        appendStatusLog(EntitlementTradeConstants.BUSINESS_PAYMENT, payment.getPaymentNo(), "CALLBACK:" + callback.getEventId(), previousStatus, EntitlementTradeConstants.PAYMENT_SUCCEEDED, EntitlementTradeConstants.OPERATOR_CHANNEL, null, "支付渠道成功回调");
    }

    private void markOrderPaidAndFulfilled(EntitlementOrder order, PaymentRecord payment, LocalDateTime now) {
        String previousStatus = order.getStatus();

        order.setStatus(EntitlementTradeConstants.ORDER_PAID);
        order.setPaidAmountCents(payment.getPaidAmountCents());
        order.setPaidTime(now);
        increaseOrderVersion(order);
        order.setUpdateTime(now);

        if (orderMapper.updateById(order) != 1) {
            throw new IllegalStateException("订单支付状态更新失败");
        }

        appendStatusLog(EntitlementTradeConstants.BUSINESS_ORDER, order.getOrderNo(), "PAID:" + payment.getPaymentNo(), previousStatus, EntitlementTradeConstants.ORDER_PAID, EntitlementTradeConstants.OPERATOR_CHANNEL, null, "订单支付成功");

        previousStatus = order.getStatus();
        order.setStatus(EntitlementTradeConstants.ORDER_FULFILLED);
        order.setFulfilledTime(now);
        increaseOrderVersion(order);
        order.setUpdateTime(now);

        if (orderMapper.updateById(order) != 1) {
            throw new IllegalStateException("订单履约状态更新失败");
        }

        appendStatusLog(EntitlementTradeConstants.BUSINESS_ORDER, order.getOrderNo(), "FULFILL:" + payment.getPaymentNo(), previousStatus, EntitlementTradeConstants.ORDER_FULFILLED, EntitlementTradeConstants.OPERATOR_SYSTEM, null, "支付成功并完成权益发放");
    }

    private void validateAssociation(PaymentRecord payment, EntitlementOrder order, VerifiedPaymentCallback callback) {
        if (!Objects.equals(payment.getOrderNo(), order.getOrderNo()) || !Objects.equals(payment.getUserId(), order.getUserId())) {
            throw new IllegalStateException("支付记录与订单关联不一致");
        }

        if (!Objects.equals(payment.getBusinessKey(), callback.getBusinessKey()) || !payment.getChannel().equalsIgnoreCase(callback.getChannel())) {
            throw new IllegalArgumentException("支付回调与支付记录不匹配");
        }

        if (!Objects.equals(payment.getAmountCents(), order.getAmountCents()) || !Objects.equals(payment.getAmountCents(), callback.getPaidAmountCents())) {
            throw new IllegalArgumentException("支付回调金额不一致");
        }
    }

    private void validateExternalIdentifiers(PaymentRecord payment, VerifiedPaymentCallback callback) {

        PaymentRecord eventOwner = paymentMapper.selectByChannelEvent(callback.getChannel(), callback.getEventId());

        if (eventOwner != null && !Objects.equals(eventOwner.getPaymentNo(), payment.getPaymentNo())) {
            throw new IllegalArgumentException("支付事件号已被其他支付记录使用");
        }

        PaymentRecord transactionOwner = paymentMapper.selectByChannelTransaction(callback.getChannel(), callback.getChannelTransactionNo());

        if (transactionOwner != null && !Objects.equals(transactionOwner.getPaymentNo(), payment.getPaymentNo())) {
            throw new IllegalArgumentException("渠道交易号已被其他支付记录使用");
        }
    }

    private void validateRepeatedCallback(PaymentRecord payment, VerifiedPaymentCallback callback) {

        if (!Objects.equals(payment.getPaidAmountCents(), callback.getPaidAmountCents()) || !Objects.equals(payment.getChannelTransactionNo(), callback.getChannelTransactionNo())) {
            throw new IllegalArgumentException("重复支付回调内容与首次结果不一致");
        }

        if (Objects.equals(payment.getCallbackEventId(), callback.getEventId()) && !Objects.equals(payment.getCallbackPayloadHash(), callback.getPayloadHash())) {
            throw new IllegalArgumentException("相同支付事件号的回调内容发生变化");
        }
    }

    private void requireCompatibleMode(NetworkEntitlement entitlement, String targetMode, LocalDateTime now) {

        if (entitlement == null || !Integer.valueOf(1).equals(entitlement.getStatus()) || targetMode.equalsIgnoreCase(entitlement.getMode())) {
            return;
        }

        if (EntitlementTradeConstants.MODE_DURATION.equalsIgnoreCase(entitlement.getMode()) && purchaseMapper.selectRefundReservedByUserForUpdate(entitlement.getUserId()) != null) {
            throw new IllegalArgumentException("存在退款冻结批次，暂时不能切换权益模式");
        }

        if (EntitlementTradeConstants.MODE_DURATION.equalsIgnoreCase(entitlement.getMode()) && entitlement.getRemainingSeconds() != null && entitlement.getRemainingSeconds() > 0) {
            throw new IllegalArgumentException("原有购买时长尚未用完");
        }

        if (EntitlementTradeConstants.MODE_SUBSCRIPTION.equalsIgnoreCase(entitlement.getMode()) && entitlement.getSubscriptionEndTime() != null && entitlement.getSubscriptionEndTime().isAfter(now)) {
            throw new IllegalArgumentException("原有订阅尚未到期");
        }
    }

    private void validateCallback(VerifiedPaymentCallback callback) {

        if (callback == null
                || !StringUtils.hasText(callback.getChannel())
                || !StringUtils.hasText(callback.getBusinessKey())
                || !StringUtils.hasText(callback.getEventId())
                || !StringUtils.hasText(callback.getChannelTransactionNo())
                || !StringUtils.hasText(callback.getPayloadHash())
                || callback.getPaidAmountCents() == null
                || callback.getPaidAmountCents() <= 0) {
            throw new IllegalArgumentException("支付回调内容无效");
        }
    }

    private long callbackPaidAmount(PaymentRecord payment) {
        if (payment.getAmountCents() == null || payment.getAmountCents() <= 0) {
            throw new IllegalStateException("支付记录金额无效");
        }
        return payment.getAmountCents();
    }

    private void increaseOrderVersion(EntitlementOrder order) {
        order.setVersion(order.getVersion() == null ? 1 : order.getVersion() + 1);
    }

    private void appendStatusLog(String businessType, String businessNo, String eventKey, String fromStatus, String toStatus, String operatorType, Long operatorId, String remark) {

        TradeStatusLog log = new TradeStatusLog();
        log.setBusinessType(businessType);
        log.setBusinessNo(businessNo);
        log.setEventKey(eventKey);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setRemark(remark);
        log.setCreateTime(LocalDateTime.now());

        statusLogMapper.insertIgnore(log);
    }

    private PaymentCallbackResultVO buildResult(PaymentRecord payment, EntitlementOrder order, NetworkEntitlement entitlement, boolean duplicate) {

        PaymentCallbackResultVO result = new PaymentCallbackResultVO();

        result.setPaymentNo(payment.getPaymentNo());
        result.setOrderNo(order.getOrderNo());
        result.setPaymentStatus(payment.getStatus());
        result.setOrderStatus(order.getStatus());
        result.setDuplicate(duplicate);
        result.setGrantedSeconds(order.getGrantSeconds());

        if (entitlement != null) {
            result.setEntitlementId(entitlement.getEntitlementId());
            result.setEntitlementMode(entitlement.getMode());
            result.setRemainingSeconds(entitlement.getRemainingSeconds());
            result.setSubscriptionEndTime(entitlement.getSubscriptionEndTime());
        }

        return result;
    }
}