package com.plagod.service.impl;

import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.entity.entitlement.EntitlementOrder;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.EntitlementOrderMapper;
import com.plagod.mapper.PaymentRecordMapper;
import com.plagod.vo.entitlement.EntitlementOrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntitlementOrderOwnershipTest {

    private EntitlementOrderMapper orderMapper;
    private EntitlementOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(EntitlementOrderMapper.class);
        service = new EntitlementOrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(
                service,
                "paymentMapper",
                mock(PaymentRecordMapper.class));
    }

    @Test
    void tenantAOwnerCanLockAndReadTenantAOrder() {
        EntitlementOrder order = cancelledOrder(31L, 7L, "ORD-A");
        when(orderMapper.selectOwnedOrderForUpdate(
                31L,
                "ORD-A",
                7L)).thenReturn(order);

        EntitlementOrderVO result =
                service.cancelOwnOrder(31L, 7L, "ORD-A");

        assertEquals("ORD-A", result.getOrderNo());
        verify(orderMapper).selectOwnedOrderForUpdate(
                31L,
                "ORD-A",
                7L);
        verify(orderMapper, never())
                .selectByOrderNoForUpdate("ORD-A");
    }

    @Test
    void tenantAOwnerCannotObserveTenantBOrder() {
        when(orderMapper.selectOwnedOrderForUpdate(
                31L,
                "ORD-B",
                7L)).thenReturn(null);

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.cancelOwnOrder(31L, 7L, "ORD-B"));

        assertEquals(404, exception.getHttpStatus());
        verify(orderMapper).selectOwnedOrderForUpdate(
                31L,
                "ORD-B",
                7L);
        verify(orderMapper, never())
                .selectByOrderNoForUpdate("ORD-B");
    }

    private EntitlementOrder cancelledOrder(
            Long tenantId,
            Long userId,
            String orderNo) {
        EntitlementOrder order = new EntitlementOrder();
        order.setTenantId(tenantId);
        order.setUserId(userId);
        order.setOrderNo(orderNo);
        order.setStatus(EntitlementTradeConstants.ORDER_CANCELLED);
        return order;
    }
}
