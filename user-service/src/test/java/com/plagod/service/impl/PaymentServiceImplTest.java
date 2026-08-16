package com.plagod.service.impl;

import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.entity.entitlement.PaymentRecord;
import com.plagod.mapper.PaymentRecordMapper;
import com.plagod.vo.entitlement.PaymentVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentServiceImplTest {

    @Test
    void completedPaymentRemainsReadableWhenChannelAdapterIsUnavailable() {
        PaymentRecordMapper paymentMapper = mock(PaymentRecordMapper.class);
        PaymentRecord payment = new PaymentRecord();
        payment.setTenantId(3L);
        payment.setUserId(7L);
        payment.setPaymentNo("PAY-HISTORY");
        payment.setOrderNo("ORDER-HISTORY");
        payment.setChannel("REMOVED_PROVIDER");
        payment.setStatus(EntitlementTradeConstants.PAYMENT_SUCCEEDED);
        payment.setAmountCents(100L);
        payment.setPaidAmountCents(100L);
        payment.setRefundedAmountCents(0L);

        when(paymentMapper.selectOwnedPayment(3L, "PAY-HISTORY", 7L))
                .thenReturn(payment);

        PaymentServiceImpl service = new PaymentServiceImpl();
        ReflectionTestUtils.setField(service, "paymentMapper", paymentMapper);
        ReflectionTestUtils.setField(service, "channelAdapters", Collections.emptyList());

        PaymentVO result = service.getOwnPayment(3L, 7L, "PAY-HISTORY");

        assertEquals(EntitlementTradeConstants.PAYMENT_SUCCEEDED, result.getStatus());
        assertEquals("REMOVED_PROVIDER", result.getChannel());
    }
}
