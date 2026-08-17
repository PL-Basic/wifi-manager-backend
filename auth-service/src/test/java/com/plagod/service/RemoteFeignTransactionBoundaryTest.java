package com.plagod.service;

import com.plagod.client.TenantContextClient;
import com.plagod.client.UserAccountClient;
import com.plagod.controller.TenantContextController;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.auth.AuthResultDTO;
import com.plagod.dto.tenant.TenantContextSwitchRequest;
import com.plagod.dto.user.UserAccountCreateRequest;
import com.plagod.dto.user.UserPasswordReplaceRequest;
import com.plagod.transaction.TestTransactionManager;
import com.plagod.vo.user.UserAccountCreateResultVO;
import com.plagod.vo.user.UserAccountSnapshotVO;
import com.plagod.vo.user.UserAuthenticationSnapshotVO;
import com.plagod.vo.user.UserPasswordReplaceResultVO;
import com.plagod.vo.tenant.TenantContextVO;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoteFeignTransactionBoundaryTest {

    @Test
    void everyUserFeignCallSuspendsCallingTransaction() {
        UserAccountClient client = mock(UserAccountClient.class);
        TestTransactionManager transactionManager =
                new TestTransactionManager();
        UserAccountGateway gateway = new UserAccountGateway(
                client,
                transactionManager,
                "test-internal-token");

        when(client.create(anyString(), any())).thenAnswer(invocation -> {
            assertOutsideTransaction();
            return ApiResponse.success(mock(UserAccountCreateResultVO.class));
        });
        when(client.findById(anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    assertOutsideTransaction();
                    return ApiResponse.success(
                            mock(UserAccountSnapshotVO.class));
                });
        when(client.findAuthenticationById(anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    assertOutsideTransaction();
                    return ApiResponse.success(
                            mock(UserAuthenticationSnapshotVO.class));
                });
        when(client.findByLogin(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    assertOutsideTransaction();
                    return ApiResponse.success(
                            mock(UserAuthenticationSnapshotVO.class));
                });
        when(client.replacePassword(anyString(), any()))
                .thenAnswer(invocation -> {
                    assertOutsideTransaction();
                    return ApiResponse.success(
                            mock(UserPasswordReplaceResultVO.class));
                });
        when(client.dispatchDefaultMembership(anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    assertOutsideTransaction();
                    return ApiResponse.success(null);
                });

        new TransactionTemplate(transactionManager).execute(status -> {
            assertTrue(TransactionSynchronizationManager
                    .isActualTransactionActive());
            gateway.create(new UserAccountCreateRequest());
            gateway.findById(7L);
            gateway.findAuthenticationById(7L);
            gateway.findByLogin("username", "alice");
            gateway.replacePassword(new UserPasswordReplaceRequest());
            gateway.dispatchDefaultMembership(7L);
            return null;
        });
    }

    @Test
    void tenantControllerFeignCallSuspendsCallingTransaction() {
        TenantContextClient client = mock(TenantContextClient.class);
        AuthSessionService authSessionService =
                mock(AuthSessionService.class);
        TestTransactionManager transactionManager =
                new TestTransactionManager();
        TenantContextVO context = new TenantContextVO();
        context.setContextType("TENANT");
        context.setTenantId("11");
        when(client.resolve(anyString(), any())).thenAnswer(invocation -> {
            assertOutsideTransaction();
            return ApiResponse.success(context);
        });
        when(authSessionService.switchContext(
                anyString(),
                anyLong(),
                any(),
                any())).thenReturn(new AuthResultDTO());
        TenantContextController controller = new TenantContextController(
                client,
                authSessionService,
                transactionManager,
                "test-internal-token");
        TenantContextSwitchRequest request =
                new TenantContextSwitchRequest();
        request.setTenantId("11");

        new TransactionTemplate(transactionManager).execute(status ->
                controller.switchTenant(
                        "session-id",
                        7L,
                        2,
                        request));
    }

    private void assertOutsideTransaction() {
        assertFalse(TransactionSynchronizationManager
                .isActualTransactionActive());
    }
}
