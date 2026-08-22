package com.plagod.controller;

import com.plagod.exception.ApiStatusException;
import com.plagod.security.TrustedRequestContextResolver;
import com.plagod.security.UserRequestContextPolicy;
import com.plagod.service.EntitlementOrderService;
import com.plagod.vo.entitlement.EntitlementOrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static com.plagod.security.UserRequestContextPolicyTest.platformRequest;
import static com.plagod.security.UserRequestContextPolicyTest.tenantRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EntitlementOrderControllerContextTest {

    private EntitlementOrderService orderService;
    private EntitlementOrderController controller;

    @BeforeEach
    void setUp() {
        orderService = mock(EntitlementOrderService.class);
        controller = new EntitlementOrderController();
        ReflectionTestUtils.setField(
                controller,
                "orderService",
                orderService);
        ReflectionTestUtils.setField(
                controller,
                "contextPolicy",
                new UserRequestContextPolicy(
                        new TrustedRequestContextResolver()));
    }

    @Test
    void tenantAReadsTenantAOrderWithFrozenActor() {
        EntitlementOrderVO order = new EntitlementOrderVO();
        order.setOrderNo("ORD-A");
        when(orderService.getOwnOrder(31L, 7L, "ORD-A"))
                .thenReturn(order);

        EntitlementOrderVO result = controller.getOrder(
                tenantRequest(7L, 31L),
                "ORD-A").getData();

        assertEquals("ORD-A", result.getOrderNo());
        verify(orderService).getOwnOrder(31L, 7L, "ORD-A");
    }

    @Test
    void platformIsRejectedBeforeTenantServiceCall() {
        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> controller.getOrder(
                        platformRequest(),
                        "ORD-A"));

        assertEquals(403, exception.getHttpStatus());
        verifyNoInteractions(orderService);
    }

    @Test
    void internalTokenWithoutActorIsRejectedBeforeServiceCall() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                com.plagod.security.TrustedRequestHeaders
                        .TRUSTED_SOURCE_ATTRIBUTE,
                com.plagod.security.TrustedRequestHeaders
                        .SOURCE_INTERNAL);
        request.addHeader(
                com.plagod.request.RequestId.HEADER_NAME,
                "request-internal-0002");

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> controller.getOrder(request, "ORD-A"));

        assertEquals(403, exception.getHttpStatus());
        verifyNoInteractions(orderService);
    }
}
