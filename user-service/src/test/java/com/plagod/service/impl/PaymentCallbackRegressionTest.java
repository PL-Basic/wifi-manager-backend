package com.plagod.service.impl;

import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.dto.entitlement.VerifiedPaymentCallback;
import com.plagod.entity.entitlement.EntitlementOrder;
import com.plagod.entity.entitlement.NetworkEntitlement;
import com.plagod.entity.entitlement.PaymentRecord;
import com.plagod.entity.user.User;
import com.plagod.mapper.DurationPurchaseMapper;
import com.plagod.mapper.EntitlementOrderMapper;
import com.plagod.mapper.EntitlementUsageLogMapper;
import com.plagod.mapper.NetworkEntitlementMapper;
import com.plagod.mapper.PaymentRecordMapper;
import com.plagod.mapper.TradeStatusLogMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.vo.entitlement.PaymentCallbackResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCallbackRegressionTest {

    private static final Long USER_ID = 7L;
    private static final String ORDER_NO = "ORDER-212C-001";
    private static final String PAYMENT_NO = "PAYMENT-212C-001";
    private static final String BUSINESS_KEY = "BUSINESS-212C-001";
    private static final String CHANNEL = "local-demo";
    private static final String EVENT_ID = "EVENT-212C-001";
    private static final String TRANSACTION_NO = "TX-212C-001";
    private static final String PAYLOAD_HASH = "payload-hash-001";
    private static final Long AMOUNT_CENTS = 100L;

    @Mock
    private EntitlementOrderMapper orderMapper;

    @Mock
    private PaymentRecordMapper paymentMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private NetworkEntitlementMapper entitlementMapper;

    @Mock
    private DurationPurchaseMapper purchaseMapper;

    @Mock
    private EntitlementUsageLogMapper usageLogMapper;

    @Mock
    private TradeStatusLogMapper statusLogMapper;

    private PaymentCallbackServiceImpl paymentCallbackService;

    @BeforeEach
    void setUp() {
        paymentCallbackService = new PaymentCallbackServiceImpl();

        ReflectionTestUtils.setField(paymentCallbackService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(paymentCallbackService, "paymentMapper", paymentMapper);
        ReflectionTestUtils.setField(paymentCallbackService, "userMapper", userMapper);
        ReflectionTestUtils.setField(paymentCallbackService, "entitlementMapper", entitlementMapper);
        ReflectionTestUtils.setField(paymentCallbackService, "purchaseMapper", purchaseMapper);
        ReflectionTestUtils.setField(paymentCallbackService, "usageLogMapper", usageLogMapper);
        ReflectionTestUtils.setField(paymentCallbackService, "statusLogMapper", statusLogMapper);
    }

    @Test
    void repeatedSuccessfulCallbackReturnsExistingResultWithoutGrantingAgain() {
        VerifiedPaymentCallback callback = successfulCallback();
        PaymentRecord payment = succeededPayment();
        EntitlementOrder order = fulfilledOrder();
        NetworkEntitlement entitlement = existingEntitlement();

        when(paymentMapper.selectByBusinessKey(BUSINESS_KEY)).thenReturn(payment);
        when(orderMapper.selectByOrderNoForUpdate(ORDER_NO)).thenReturn(order);
        when(paymentMapper.selectByPaymentNoForUpdate(PAYMENT_NO)).thenReturn(payment);

        // 相同外部事件和交易号仍归属于当前支付记录。
        when(paymentMapper.selectByChannelEvent(CHANNEL, EVENT_ID)).thenReturn(payment);
        when(paymentMapper.selectByChannelTransaction(CHANNEL, TRANSACTION_NO)).thenReturn(payment);

        when(userMapper.selectByIdForUpdate(USER_ID)).thenReturn(new User());
        when(entitlementMapper.selectByUserIdForUpdate(USER_ID)).thenReturn(entitlement);

        PaymentCallbackResultVO result = paymentCallbackService.handleSuccess(callback);

        assertNotNull(result);
        assertTrue(result.isDuplicate());
        assertEquals(PAYMENT_NO, result.getPaymentNo());
        assertEquals(ORDER_NO, result.getOrderNo());
        assertEquals(EntitlementTradeConstants.PAYMENT_SUCCEEDED, result.getPaymentStatus());
        assertEquals(EntitlementTradeConstants.ORDER_FULFILLED, result.getOrderStatus());
        assertEquals(entitlement.getEntitlementId(), result.getEntitlementId());
        assertEquals(entitlement.getRemainingSeconds(), result.getRemainingSeconds());

        // 重复回调只能读取已经提交的结果，不能再次发放权益。
        verify(paymentMapper, never()).updateById(any(PaymentRecord.class));
        verify(orderMapper, never()).updateById(any(EntitlementOrder.class));
        verify(entitlementMapper, never()).insert(any(NetworkEntitlement.class));
        verify(entitlementMapper, never()).updateById(any(NetworkEntitlement.class));
        verify(purchaseMapper, never()).insert(any());
        verify(usageLogMapper, never()).insert(any());
        verify(statusLogMapper, never()).insertIgnore(any());
    }

    private VerifiedPaymentCallback successfulCallback() {
        VerifiedPaymentCallback callback = new VerifiedPaymentCallback();
        callback.setChannel(CHANNEL);
        callback.setBusinessKey(BUSINESS_KEY);
        callback.setEventId(EVENT_ID);
        callback.setChannelTransactionNo(TRANSACTION_NO);
        callback.setPaidAmountCents(AMOUNT_CENTS);
        callback.setPayloadHash(PAYLOAD_HASH);
        return callback;
    }

    private PaymentRecord succeededPayment() {
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo(PAYMENT_NO);
        payment.setOrderNo(ORDER_NO);
        payment.setUserId(USER_ID);
        payment.setBusinessKey(BUSINESS_KEY);
        payment.setChannel(CHANNEL);
        payment.setAmountCents(AMOUNT_CENTS);
        payment.setPaidAmountCents(AMOUNT_CENTS);
        payment.setStatus(EntitlementTradeConstants.PAYMENT_SUCCEEDED);
        payment.setChannelTransactionNo(TRANSACTION_NO);
        payment.setCallbackEventId(EVENT_ID);
        payment.setCallbackPayloadHash(PAYLOAD_HASH);
        return payment;
    }

    private EntitlementOrder fulfilledOrder() {
        EntitlementOrder order = new EntitlementOrder();
        order.setOrderNo(ORDER_NO);
        order.setUserId(USER_ID);
        order.setAmountCents(AMOUNT_CENTS);
        order.setGrantSeconds(3600L);
        order.setStatus(EntitlementTradeConstants.ORDER_FULFILLED);
        return order;
    }

    private NetworkEntitlement existingEntitlement() {
        NetworkEntitlement entitlement = new NetworkEntitlement();
        entitlement.setEntitlementId(21L);
        entitlement.setUserId(USER_ID);
        entitlement.setMode(EntitlementTradeConstants.MODE_DURATION);
        entitlement.setRemainingSeconds(7200L);
        entitlement.setStatus(1);
        return entitlement;
    }
}