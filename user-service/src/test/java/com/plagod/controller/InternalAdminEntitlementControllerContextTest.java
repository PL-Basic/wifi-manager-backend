package com.plagod.controller;

import com.plagod.dto.entitlement.EntitlementRewardOrderRequest;
import com.plagod.dto.entitlement.UnlimitedEntitlementRequest;
import com.plagod.exception.ApiStatusException;
import com.plagod.security.TrustedRequestContextResolver;
import com.plagod.security.UserRequestContextPolicy;
import com.plagod.service.EntitlementAdjustmentService;
import com.plagod.service.EntitlementRewardOrderService;
import com.plagod.service.UserManageService;
import com.plagod.vo.user.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static com.plagod.security.UserRequestContextPolicyTest.platformRequest;
import static com.plagod.security.UserRequestContextPolicyTest.platformTenantRequest;
import static com.plagod.security.UserRequestContextPolicyTest.tenantAdminRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InternalAdminEntitlementControllerContextTest {

    private EntitlementAdjustmentService adjustmentService;
    private EntitlementRewardOrderService rewardOrderService;
    private UserManageService userManageService;
    private InternalAdminEntitlementController controller;

    @BeforeEach
    void setUp() {
        adjustmentService = mock(EntitlementAdjustmentService.class);
        rewardOrderService = mock(EntitlementRewardOrderService.class);
        userManageService = mock(UserManageService.class);
        controller = new InternalAdminEntitlementController();
        ReflectionTestUtils.setField(
                controller,
                "adjustmentService",
                adjustmentService);
        ReflectionTestUtils.setField(
                controller,
                "rewardOrderService",
                rewardOrderService);
        ReflectionTestUtils.setField(
                controller,
                "userManageService",
                userManageService);
        ReflectionTestUtils.setField(
                controller,
                "contextPolicy",
                new UserRequestContextPolicy(
                        new TrustedRequestContextResolver()));
    }

    @Test
    void tenantAdminCannotInvokeHighPrivilegeOperations() {
        ApiStatusException unlimitedException = assertThrows(
                ApiStatusException.class,
                () -> controller.adjustUnlimited(
                        9L,
                        tenantAdminRequest(7L, 31L),
                        new UnlimitedEntitlementRequest()));
        ApiStatusException rewardException = assertThrows(
                ApiStatusException.class,
                () -> controller.createRewardOrder(
                        9L,
                        tenantAdminRequest(7L, 31L),
                        new EntitlementRewardOrderRequest()));

        assertEquals(403, unlimitedException.getHttpStatus());
        assertEquals(403, rewardException.getHttpStatus());
        verifyNoInteractions(
                adjustmentService,
                rewardOrderService,
                userManageService);
    }

    @Test
    void platformCannotInvokeHighPrivilegeOperations() {
        ApiStatusException unlimitedException = assertThrows(
                ApiStatusException.class,
                () -> controller.adjustUnlimited(
                        9L,
                        platformRequest(),
                        new UnlimitedEntitlementRequest()));
        ApiStatusException rewardException = assertThrows(
                ApiStatusException.class,
                () -> controller.createRewardOrder(
                        9L,
                        platformRequest(),
                        new EntitlementRewardOrderRequest()));

        assertEquals(403, unlimitedException.getHttpStatus());
        assertEquals(403, rewardException.getHttpStatus());
        verifyNoInteractions(
                adjustmentService,
                rewardOrderService,
                userManageService);
    }

    @Test
    void platformTenantSuperAdminUsesTrustedTenantForHighPrivilegeOperations() {
        UserVO actor = new UserVO();
        actor.setUsername("platform-admin");
        when(userManageService.getUser(1L)).thenReturn(actor);
        UnlimitedEntitlementRequest unlimitedRequest =
                new UnlimitedEntitlementRequest();
        EntitlementRewardOrderRequest rewardRequest =
                new EntitlementRewardOrderRequest();

        controller.adjustUnlimited(
                9L,
                platformTenantRequest(1L, 31L),
                unlimitedRequest);
        controller.createRewardOrder(
                9L,
                platformTenantRequest(1L, 31L),
                rewardRequest);

        verify(adjustmentService).adjustUnlimited(
                31L,
                9L,
                1L,
                "platform-admin",
                0,
                unlimitedRequest);
        verify(rewardOrderService).create(
                31L,
                9L,
                1L,
                "platform-admin",
                rewardRequest);
    }
}
