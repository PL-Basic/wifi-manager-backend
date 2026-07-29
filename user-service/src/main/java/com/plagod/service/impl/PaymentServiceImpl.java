package com.plagod.service.impl;

import com.plagod.configuration.PaymentProperties;
import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.dto.entitlement.LocalDemoPaymentCallbackRequest;
import com.plagod.dto.entitlement.PaymentCreateRequest;
import com.plagod.dto.entitlement.VerifiedPaymentCallback;
import com.plagod.entity.entitlement.*;
import com.plagod.entity.user.User;
import com.plagod.mapper.*;
import com.plagod.service.PaymentCallbackService;
import com.plagod.service.PaymentService;
import com.plagod.service.payment.LocalDemoPaymentChannelAdapter;
import com.plagod.service.payment.PaymentChannelAdapter;
import com.plagod.vo.entitlement.PaymentCallbackResultVO;
import com.plagod.vo.entitlement.PaymentVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private PaymentProperties paymentProperties;
    @Autowired
    private EntitlementOrderMapper orderMapper;
    @Autowired
    private PaymentRecordMapper paymentMapper;
    @Autowired
    private NetworkEntitlementMapper entitlementMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private TradeStatusLogMapper statusLogMapper;
    @Autowired
    private List<PaymentChannelAdapter> channelAdapters;
    @Autowired
    private PaymentCallbackService paymentCallbackService;
    @Autowired
    private LocalDemoPaymentChannelAdapter localDemoAdapter;
    @Autowired
    private DurationPurchaseMapper purchaseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentVO createPayment(Long userId, String rawOrderNo, PaymentCreateRequest request) {

        requireUserId(userId);
        if (request == null) {
            throw new IllegalArgumentException("支付参数不能为空");
        }

        String orderNo = normalize(rawOrderNo, "订单号", 64, true);
        String requestId = normalize(request.getRequestId(), "支付请求号", 64, false);
        String channel = paymentProperties.normalizeChannel(request.getChannel());
        PaymentChannelAdapter adapter = requireAdapter(channel);

        // 固定锁顺序从订单开始。
        EntitlementOrder order = orderMapper.selectByOrderNoForUpdate(orderNo);

        if (order == null || !userId.equals(order.getUserId())) {
            throw new IllegalArgumentException("订单不存在或不属于当前用户");
        }

        // 同一个订单只允许存在一条支付记录，避免订单被支付两次。
        PaymentRecord existing = paymentMapper.selectByOrderNoForUpdate(orderNo);

        if (existing != null) {
            requireSameRequest(existing, userId, requestId, channel);
            return toVO(existing, requireAction(adapter, existing));
        }

        PaymentRecord reusedRequest = paymentMapper.selectByUserRequestForUpdate(userId, requestId);

        if (reusedRequest != null) {
            throw new IllegalArgumentException("支付请求号已被其他订单使用");
        }

        if (!EntitlementTradeConstants.ORDER_PENDING_PAYMENT.equals(order.getStatus())) {
            throw new IllegalArgumentException("当前订单状态不能发起支付");
        }

        LocalDateTime now = LocalDateTime.now();
        if (order.getExpireTime() == null || !order.getExpireTime().isAfter(now)) {
            throw new IllegalArgumentException("订单已经过期");
        }

        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new IllegalArgumentException("用户不存在或不可用");
        }

        NetworkEntitlement entitlement = entitlementMapper.selectByUserIdForUpdate(userId);
        requireCompatibleMode(entitlement, order.getEntitlementMode(), now);

        PaymentRecord conflicting = paymentMapper.selectCreatedOtherModePayment(userId, order.getEntitlementMode());

        if (conflicting != null) {
            throw new IllegalArgumentException("已有其他权益模式的待支付记录");
        }

        PaymentRecord candidate = new PaymentRecord();
        candidate.setPaymentNo(generateNo("PAY", now));
        candidate.setOrderNo(orderNo);
        candidate.setUserId(userId);
        candidate.setRequestId(requestId);
        candidate.setBusinessKey("PBK" + randomText());
        candidate.setChannel(channel);
        candidate.setAmountCents(order.getAmountCents());
        candidate.setPaidAmountCents(0L);
        candidate.setRefundedAmountCents(0L);
        candidate.setStatus(EntitlementTradeConstants.PAYMENT_CREATED);
        candidate.setVersion(0);
        candidate.setCreateTime(now);
        candidate.setUpdateTime(now);

        paymentMapper.insertOrResolveExisting(candidate);

        PaymentRecord stored = paymentMapper.selectByOrderNoForUpdate(orderNo);
        if (stored == null) {
            throw new IllegalStateException("支付创建结果无法确认");
        }

        requireSameRequest(stored, userId, requestId, channel);

        appendStatusLog(stored.getPaymentNo(), "CREATE:" + requestId, null, EntitlementTradeConstants.PAYMENT_CREATED, userId);

        return toVO(stored, requireAction(adapter, stored));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentVO getOwnPayment(Long userId, String rawPaymentNo) {
        requireUserId(userId);

        String paymentNo = normalize(rawPaymentNo, "支付单号", 64, true);
        PaymentRecord payment = paymentMapper.selectOwnedPayment(paymentNo, userId);

        if (payment == null) {
            throw new IllegalArgumentException("支付记录不存在或不属于当前用户");
        }

        PaymentChannelAdapter adapter = requireAdapter(payment.getChannel());

        return toVO(payment, requireAction(adapter, payment));
    }

    @Override
    public PaymentCallbackResultVO completeLocalDemo(Long userId, String rawPaymentNo) {

        requireUserId(userId);

        String paymentNo = normalize(rawPaymentNo, "支付单号", 64, true);

        PaymentRecord payment = paymentMapper.selectOwnedPayment(paymentNo, userId);

        if (payment == null) {
            throw new IllegalArgumentException("支付记录不存在或不属于当前用户");
        }

        if (!EntitlementTradeConstants.CHANNEL_LOCAL_DEMO.equals(payment.getChannel())) {
            throw new IllegalArgumentException("当前支付记录不是本地 Demo 渠道");
        }

        if (!EntitlementTradeConstants.PAYMENT_CREATED.equals(payment.getStatus())
                && !EntitlementTradeConstants.PAYMENT_SUCCEEDED.equals(payment.getStatus())) {
            throw new IllegalArgumentException("当前支付状态不能执行 Demo 支付");
        }

        LocalDemoPaymentCallbackRequest callbackRequest = localDemoAdapter.buildSuccessCallback(payment);

        VerifiedPaymentCallback callback = localDemoAdapter.verify(callbackRequest);


        return paymentCallbackService.handleSuccess(callback);
    }

    private PaymentChannelAdapter.PaymentChannelAction requireAction(PaymentChannelAdapter adapter, PaymentRecord payment) {

        if (!EntitlementTradeConstants.PAYMENT_CREATED.equals(payment.getStatus())) {
            return null;
        }
        return adapter.initiate(payment);
    }

    private void requireCompatibleMode(NetworkEntitlement entitlement, String targetMode, LocalDateTime now) {

        if (entitlement == null || !Integer.valueOf(1).equals(entitlement.getStatus()) || targetMode.equalsIgnoreCase(entitlement.getMode())) {
            return;
        }

        if (EntitlementTradeConstants.MODE_DURATION.equalsIgnoreCase(entitlement.getMode()) && purchaseMapper.selectRefundReservedByUserForUpdate(entitlement.getUserId()) != null) {
            throw new IllegalArgumentException("存在退款冻结批次，暂时不能切换权益模式");
        }

        if (EntitlementTradeConstants.MODE_DURATION.equalsIgnoreCase(entitlement.getMode()) && entitlement.getRemainingSeconds() != null && entitlement.getRemainingSeconds() > 0) {
            throw new IllegalArgumentException("现有购买时长尚未用完，不能切换为订阅权益");
        }

        if (EntitlementTradeConstants.MODE_SUBSCRIPTION.equalsIgnoreCase(entitlement.getMode()) && entitlement.getSubscriptionEndTime() != null && entitlement.getSubscriptionEndTime().isAfter(now)) {
            throw new IllegalArgumentException("现有订阅尚未到期，不能切换为时长权益");
        }
    }

    private PaymentChannelAdapter requireAdapter(String channel) {
        for (PaymentChannelAdapter adapter : channelAdapters) {
            if (adapter.channel().equalsIgnoreCase(channel)) {
                return adapter;
            }
        }
        throw new IllegalArgumentException("暂不支持该支付渠道");
    }

    private void requireSameRequest(PaymentRecord payment, Long userId, String requestId, String channel) {

        if (!userId.equals(payment.getUserId()) || !requestId.equals(payment.getRequestId()) || !channel.equals(payment.getChannel())) {
            throw new IllegalArgumentException("订单已经使用其他支付请求创建支付记录");
        }
    }

    private void appendStatusLog(String paymentNo, String eventKey, String fromStatus, String toStatus, Long operatorId) {

        TradeStatusLog log = new TradeStatusLog();
        log.setBusinessType(EntitlementTradeConstants.BUSINESS_PAYMENT);
        log.setBusinessNo(paymentNo);
        log.setEventKey(eventKey);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperatorType(EntitlementTradeConstants.OPERATOR_USER);
        log.setOperatorId(operatorId);
        log.setRemark("用户创建支付记录");
        log.setCreateTime(LocalDateTime.now());
        statusLogMapper.insertIgnore(log);
    }

    private PaymentVO toVO(PaymentRecord payment, PaymentChannelAdapter.PaymentChannelAction action) {

        PaymentVO vo = new PaymentVO();
        vo.setPaymentNo(payment.getPaymentNo());
        vo.setOrderNo(payment.getOrderNo());
        vo.setChannel(payment.getChannel());
        vo.setAmountCents(payment.getAmountCents());
        vo.setPaidAmountCents(payment.getPaidAmountCents());
        vo.setRefundedAmountCents(payment.getRefundedAmountCents());
        vo.setStatus(payment.getStatus());
        vo.setBusinessKey(payment.getBusinessKey());
        vo.setChannelTransactionNo(payment.getChannelTransactionNo());
        vo.setPaidTime(payment.getPaidTime());
        vo.setCreateTime(payment.getCreateTime());

        if (action != null) {
            vo.setActionType(action.getType());
            vo.setActionValue(action.getValue());
        }
        return vo;
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户身份无效");
        }
    }

    private String normalize(String value, String fieldName, int maxLength, boolean upperCase) {

        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }

        String normalized = value.trim();
        if (upperCase) {
            normalized = normalized.toUpperCase(Locale.ROOT);
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private String generateNo(String prefix, LocalDateTime now) {
        return prefix + TIME_FORMAT.format(now) + randomText().substring(0, 20);
    }

    private String randomText() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }
}