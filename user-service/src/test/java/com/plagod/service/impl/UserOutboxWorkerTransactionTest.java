package com.plagod.service.impl;

import com.plagod.client.AuthSessionClient;
import com.plagod.client.TenantMembershipClient;
import com.plagod.dto.ApiResponse;
import com.plagod.entity.auth.DefaultTenantMembershipOutbox;
import com.plagod.entity.auth.UserAuthSessionRevokeOutbox;
import com.plagod.mapper.DefaultTenantMembershipOutboxMapper;
import com.plagod.mapper.UserAuthSessionRevokeOutboxMapper;
import com.plagod.support.NoOpTransactionManager;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserOutboxWorkerTransactionTest {

    @Test
    void exhaustedLegacyRowsAreTerminalizedBeforeDispatchScan() {
        DefaultTenantMembershipOutboxMapper mapper =
                mock(DefaultTenantMembershipOutboxMapper.class);
        TenantMembershipClient client =
                mock(TenantMembershipClient.class);
        when(mapper.finalizeExhausted(any(), anyInt(), anyString()))
                .thenAnswer(invocation -> {
                    assertTrue(transactionActive());
                    return 1;
                });
        when(mapper.selectDispatchableIds(any(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        DefaultTenantMembershipOutboxServiceImpl service =
                new DefaultTenantMembershipOutboxServiceImpl(
                        mapper,
                        client,
                        new NoOpTransactionManager());

        service.dispatchPending(20);

        InOrder order = inOrder(mapper);
        order.verify(mapper).finalizeExhausted(
                any(),
                org.mockito.ArgumentMatchers.eq(3),
                org.mockito.ArgumentMatchers.eq(
                        "RETRY_LIMIT_EXHAUSTED"));
        order.verify(mapper).selectDispatchableIds(
                any(),
                org.mockito.ArgumentMatchers.eq(3),
                org.mockito.ArgumentMatchers.eq(20));
    }

    @Test
    void tenantCallRunsAfterClaimCommitAndFinalizeUsesNewTransaction() {
        DefaultTenantMembershipOutboxMapper mapper =
                mock(DefaultTenantMembershipOutboxMapper.class);
        TenantMembershipClient client =
                mock(TenantMembershipClient.class);
        DefaultTenantMembershipOutbox outbox =
                membershipOutbox();

        when(mapper.selectDispatchableIds(any(), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(17L));
        when(mapper.claim(
                anyLong(),
                anyString(),
                any(),
                any(),
                anyInt())).thenAnswer(invocation -> {
                    assertTrue(transactionActive());
                    return 1;
                });
        when(mapper.selectById(17L)).thenReturn(outbox);
        when(client.ensureDefaultMembership(any()))
                .thenAnswer(invocation -> {
                    assertFalse(transactionActive());
                    return ApiResponse.success(null);
                });
        when(mapper.finalizeSucceeded(anyLong(), anyString()))
                .thenAnswer(invocation -> {
                    assertTrue(transactionActive());
                    return 1;
                });

        DefaultTenantMembershipOutboxServiceImpl service =
                new DefaultTenantMembershipOutboxServiceImpl(
                        mapper,
                        client,
                        new NoOpTransactionManager());

        service.dispatchPending(1);

        verify(mapper).finalizeSucceeded(
                anyLong(),
                anyString());
    }

    @Test
    void authCallKeepsExistingEndpointOutsideTransactions() {
        UserAuthSessionRevokeOutboxMapper mapper =
                mock(UserAuthSessionRevokeOutboxMapper.class);
        AuthSessionClient client =
                mock(AuthSessionClient.class);
        UserAuthSessionRevokeOutbox outbox =
                revokeOutbox();

        when(mapper.selectDispatchableIds(any(), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(23L));
        when(mapper.claim(
                anyLong(),
                anyString(),
                any(),
                any(),
                anyInt())).thenAnswer(invocation -> {
                    assertTrue(transactionActive());
                    return 1;
                });
        when(mapper.selectById(23L)).thenReturn(outbox);
        when(client.revokeAll(7L, "ACCOUNT_DISABLED"))
                .thenAnswer(invocation -> {
                    assertFalse(transactionActive());
                    return ApiResponse.success(null);
                });
        when(mapper.finalizeSucceeded(
                anyLong(),
                anyString(),
                any())).thenAnswer(invocation -> {
                    assertTrue(transactionActive());
                    return 1;
                });

        UserAuthSessionRevokeOutboxServiceImpl service =
                new UserAuthSessionRevokeOutboxServiceImpl(
                        mapper,
                        client,
                        new NoOpTransactionManager());

        service.dispatchPending(1);

        verify(client).revokeAll(7L, "ACCOUNT_DISABLED");
    }

    @Test
    void failedDeliveryUsesBoundedFailureFinalize() {
        UserAuthSessionRevokeOutboxMapper mapper =
                mock(UserAuthSessionRevokeOutboxMapper.class);
        AuthSessionClient client =
                mock(AuthSessionClient.class);
        UserAuthSessionRevokeOutbox outbox =
                revokeOutbox();
        outbox.setRetryCount(2);

        when(mapper.selectDispatchableIds(any(), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(23L));
        when(mapper.claim(
                anyLong(),
                anyString(),
                any(),
                any(),
                anyInt())).thenReturn(1);
        when(mapper.selectById(23L)).thenReturn(outbox);
        when(client.revokeAll(anyLong(), anyString()))
                .thenThrow(new IllegalStateException("canary-secret"));
        when(mapper.finalizeFailed(
                anyLong(),
                anyString(),
                any(),
                any(),
                anyString(),
                anyInt())).thenAnswer(invocation -> {
                    assertTrue(transactionActive());
                    return 1;
                });

        UserAuthSessionRevokeOutboxServiceImpl service =
                new UserAuthSessionRevokeOutboxServiceImpl(
                        mapper,
                        client,
                        new NoOpTransactionManager());

        service.dispatchPending(1);

        verify(mapper).finalizeFailed(
                anyLong(),
                anyString(),
                any(),
                any(),
                anyString(),
                org.mockito.ArgumentMatchers.eq(3));
    }

    private boolean transactionActive() {
        return TransactionSynchronizationManager
                .isActualTransactionActive();
    }

    private DefaultTenantMembershipOutbox membershipOutbox() {
        DefaultTenantMembershipOutbox outbox =
                new DefaultTenantMembershipOutbox();
        outbox.setOutboxId(17L);
        outbox.setEventId("membership-event-17");
        outbox.setUserId(7L);
        outbox.setRole(2);
        outbox.setRetryCount(0);
        return outbox;
    }

    private UserAuthSessionRevokeOutbox revokeOutbox() {
        UserAuthSessionRevokeOutbox outbox =
                new UserAuthSessionRevokeOutbox();
        outbox.setOutboxId(23L);
        outbox.setEventId("auth-revoke-event-23");
        outbox.setUserId(7L);
        outbox.setRevokeReason("ACCOUNT_DISABLED");
        outbox.setRetryCount(0);
        return outbox;
    }
}
