package com.plagod.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.client.UserSocialIdentityClient;
import com.plagod.constant.OAuthProvider;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.OAuthProfile;
import com.plagod.dto.OAuthStateContext;
import com.plagod.service.oauth.OAuthProviderAdapter;
import com.plagod.service.oauth.OAuthProviderRegistry;
import com.plagod.transaction.TestTransactionManager;
import com.plagod.vo.user.SocialIdentityResolveResultVO;
import com.plagod.vo.user.SocialLoginPrincipalVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuthCallbackTransactionBoundaryTest {

    @Test
    void callbackCommitsClaimBeforeProviderAndSuspendsRemoteCalls() {
        TestTransactionManager transactionManager =
                new TestTransactionManager();
        OAuthStateTransactionService stateService =
                mock(OAuthStateTransactionService.class);
        OAuthProviderRegistry providerRegistry =
                mock(OAuthProviderRegistry.class);
        OAuthProviderAdapter provider = mock(OAuthProviderAdapter.class);
        UserSocialIdentityClient userClient =
                mock(UserSocialIdentityClient.class);
        List<String> calls = new ArrayList<>();

        when(stateService.claim("github", "state", "code"))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    calls.add("claim");
                    return stateContext();
                });
        when(providerRegistry.require("github")).thenReturn(provider);
        when(provider.provider()).thenReturn(OAuthProvider.GITHUB);
        when(provider.exchange("code")).thenAnswer(invocation -> {
            assertRemoteAfterClaimCommit(transactionManager);
            calls.add("provider");
            return profile();
        });
        when(userClient.resolve(anyString(), any()))
                .thenAnswer(invocation -> {
                    assertRemoteAfterClaimCommit(transactionManager);
                    calls.add("user");
                    return ApiResponse.success(bindReady());
                });
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager
                    .isActualTransactionActive());
            assertEquals(1, transactionManager.getCommitCount());
            calls.add("complete");
            return null;
        }).when(stateService).complete(
                any(OAuthStateContext.class),
                anyString(),
                any(),
                any());

        OAuthService service = new OAuthService();
        ReflectionTestUtils.setField(
                service,
                "userAccountGateway",
                mock(UserAccountGateway.class));
        ReflectionTestUtils.setField(
                service,
                "providerRegistry",
                providerRegistry);
        ReflectionTestUtils.setField(service, "stateService", stateService);
        ReflectionTestUtils.setField(
                service,
                "claimBoundary",
                new OAuthStateClaimTransactionBoundary(
                        stateService,
                        transactionManager));
        ReflectionTestUtils.setField(
                service,
                "remoteGateway",
                new OAuthRemoteGateway(
                        providerRegistry,
                        userClient,
                        transactionManager,
                        "test-internal-token"));
        ReflectionTestUtils.setField(
                service,
                "authSessionService",
                mock(AuthSessionService.class));
        ReflectionTestUtils.setField(
                service,
                "objectMapper",
                new ObjectMapper());

        new TransactionTemplate(transactionManager).execute(status -> {
            assertTrue(TransactionSynchronizationManager
                    .isActualTransactionActive());
            service.callback(
                    "github",
                    "state",
                    "code",
                    "client-instance",
                    "test-agent",
                    "192.168.1.23");
            assertTrue(TransactionSynchronizationManager
                    .isActualTransactionActive());
            return null;
        });

        assertEquals(
                Arrays.asList("claim", "provider", "user", "complete"),
                calls);
        assertEquals(3, transactionManager.getCommitCount());
    }

    @Test
    void completedTerminalStateSurvivesCallingTransactionRollback() {
        TestTransactionManager transactionManager =
                new TestTransactionManager();
        OAuthStateTransactionService stateService =
                mock(OAuthStateTransactionService.class);
        OAuthProviderRegistry providerRegistry =
                mock(OAuthProviderRegistry.class);
        OAuthProviderAdapter provider = mock(OAuthProviderAdapter.class);
        UserSocialIdentityClient userClient =
                mock(UserSocialIdentityClient.class);
        AtomicReference<String> terminalState = new AtomicReference<>();

        when(stateService.claim("github", "state", "code"))
                .thenReturn(stateContext());
        when(providerRegistry.require("github")).thenReturn(provider);
        when(provider.provider()).thenReturn(OAuthProvider.GITHUB);
        when(provider.exchange("code")).thenReturn(profile());
        when(userClient.resolve(anyString(), any()))
                .thenReturn(ApiResponse.success(bindReady()));
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager
                    .isActualTransactionActive());
            terminalState.set("COMPLETED");
            return null;
        }).when(stateService).complete(
                any(OAuthStateContext.class),
                anyString(),
                any(),
                any());

        OAuthService service = service(
                stateService,
                providerRegistry,
                userClient,
                transactionManager);

        new TransactionTemplate(transactionManager).execute(status -> {
            service.callback(
                    "github",
                    "state",
                    "code",
                    "client-instance",
                    "test-agent",
                    "192.168.1.23");
            status.setRollbackOnly();
            return null;
        });

        assertEquals("COMPLETED", terminalState.get());
        assertEquals(2, transactionManager.getCommitCount());
        assertEquals(1, transactionManager.getRollbackCount());
    }

    @Test
    void failedTerminalStateSurvivesCallingTransactionRollback() {
        TestTransactionManager transactionManager =
                new TestTransactionManager();
        OAuthStateTransactionService stateService =
                mock(OAuthStateTransactionService.class);
        OAuthProviderRegistry providerRegistry =
                mock(OAuthProviderRegistry.class);
        OAuthProviderAdapter provider = mock(OAuthProviderAdapter.class);
        UserSocialIdentityClient userClient =
                mock(UserSocialIdentityClient.class);
        AtomicReference<String> terminalState = new AtomicReference<>();

        when(stateService.claim("github", "state", "code"))
                .thenReturn(stateContext());
        when(providerRegistry.require("github")).thenReturn(provider);
        when(provider.provider()).thenReturn(OAuthProvider.GITHUB);
        when(provider.exchange("code"))
                .thenThrow(new IllegalStateException("provider failed"));
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager
                    .isActualTransactionActive());
            terminalState.set("FAILED");
            return null;
        }).when(stateService).fail(
                any(OAuthStateContext.class),
                anyString());

        OAuthService service = service(
                stateService,
                providerRegistry,
                userClient,
                transactionManager);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> new TransactionTemplate(transactionManager)
                        .execute(status -> {
                            service.callback(
                                    "github",
                                    "state",
                                    "code",
                                    "client-instance",
                                    "test-agent",
                                    "192.168.1.23");
                            return null;
                        }));

        assertEquals("FAILED", terminalState.get());
        assertEquals(2, transactionManager.getCommitCount());
        assertEquals(1, transactionManager.getRollbackCount());
    }

    @Test
    void socialPrincipalLookupSuspendsCallingTransaction() {
        TestTransactionManager transactionManager =
                new TestTransactionManager();
        OAuthProviderRegistry providerRegistry =
                mock(OAuthProviderRegistry.class);
        UserSocialIdentityClient userClient =
                mock(UserSocialIdentityClient.class);
        when(userClient.getPrincipal(anyString(), any()))
                .thenAnswer(invocation -> {
                    assertFalse(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return ApiResponse.success(
                            mock(SocialLoginPrincipalVO.class));
                });
        OAuthRemoteGateway gateway = new OAuthRemoteGateway(
                providerRegistry,
                userClient,
                transactionManager,
                "test-internal-token");

        new TransactionTemplate(transactionManager).execute(status -> {
            gateway.getPrincipal(7L);
            return null;
        });
    }

    private void assertRemoteAfterClaimCommit(
            TestTransactionManager transactionManager) {
        assertFalse(TransactionSynchronizationManager
                .isActualTransactionActive());
        assertEquals(1, transactionManager.getCommitCount());
    }

    private OAuthService service(
            OAuthStateTransactionService stateService,
            OAuthProviderRegistry providerRegistry,
            UserSocialIdentityClient userClient,
            TestTransactionManager transactionManager) {
        OAuthService service = new OAuthService();
        ReflectionTestUtils.setField(
                service,
                "userAccountGateway",
                mock(UserAccountGateway.class));
        ReflectionTestUtils.setField(
                service,
                "providerRegistry",
                providerRegistry);
        ReflectionTestUtils.setField(service, "stateService", stateService);
        ReflectionTestUtils.setField(
                service,
                "claimBoundary",
                new OAuthStateClaimTransactionBoundary(
                        stateService,
                        transactionManager));
        ReflectionTestUtils.setField(
                service,
                "remoteGateway",
                new OAuthRemoteGateway(
                        providerRegistry,
                        userClient,
                        transactionManager,
                        "test-internal-token"));
        ReflectionTestUtils.setField(
                service,
                "authSessionService",
                mock(AuthSessionService.class));
        ReflectionTestUtils.setField(
                service,
                "objectMapper",
                new ObjectMapper());
        return service;
    }

    private OAuthStateContext stateContext() {
        OAuthStateContext context = new OAuthStateContext();
        context.setStateId(11L);
        context.setProvider("github");
        context.setPurpose("BIND");
        context.setBindUserId(7L);
        context.setCodeHash("code-hash");
        return context;
    }

    private OAuthProfile profile() {
        OAuthProfile profile = new OAuthProfile();
        profile.setProvider("github");
        profile.setProviderSubject("github-7");
        profile.setProviderUsername("alice");
        return profile;
    }

    private SocialIdentityResolveResultVO bindReady() {
        SocialLoginPrincipalVO principal = new SocialLoginPrincipalVO();
        principal.setUserId(7L);
        principal.setUsername("alice");
        principal.setRole(2);
        return SocialIdentityResolveResultVO.bindReady(principal, null);
    }
}
