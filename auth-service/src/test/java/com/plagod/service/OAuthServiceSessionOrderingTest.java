package com.plagod.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.OAuthProfile;
import com.plagod.dto.OAuthStateContext;
import com.plagod.exception.ApiStatusException;
import com.plagod.service.oauth.OAuthProviderRegistry;
import com.plagod.vo.user.SocialIdentityResolveResultVO;
import com.plagod.vo.user.SocialLoginPrincipalVO;
import com.plagod.vo.user.UserAccountSnapshotVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthServiceSessionOrderingTest {

    @Test
    void oauthStateIsNotCompletedWhenRefreshSessionCreationFails() {
        Fixture fixture = fixture();
        when(fixture.userAccountGateway.findById(7L))
                .thenReturn(account(true));
        when(fixture.authSessionService.open(
                any(),
                anyString(),
                anyString(),
                anyString())).thenThrow(
                ApiStatusException.serviceUnavailable(
                        "租户上下文服务暂时不可用"));

        assertThrows(
                ApiStatusException.class,
                () -> fixture.service.callback(
                        "github",
                        "state",
                        "code",
                        "client-instance",
                        "test-agent",
                        "192.168.1.23"));

        verify(fixture.stateService, never()).complete(
                any(),
                anyString(),
                any(),
                anyString());
        verify(fixture.stateService).fail(any(), anyString());
    }

    @Test
    void oauthMembershipPendingDoesNotOpenRefreshSession() {
        Fixture fixture = fixture();
        when(fixture.userAccountGateway.findById(7L))
                .thenReturn(account(false));

        com.plagod.vo.OAuthCallbackIssue issue = fixture.service.callback(
                "github",
                "state",
                "code",
                "client-instance",
                "test-agent",
                "192.168.1.23");

        assertEquals(
                "TENANT_MEMBERSHIP_PENDING",
                issue.getResult().getAccountState());
        assertNull(issue.getResult().getToken());
        assertNull(issue.getSessionIssue());
        verify(fixture.userAccountGateway).dispatchDefaultMembership(7L);
        verify(fixture.authSessionService, never()).open(
                any(),
                anyString(),
                anyString(),
                anyString());
        verify(fixture.stateService).complete(
                any(),
                anyString(),
                any(),
                anyString());
    }

    private Fixture fixture() {
        OAuthService service = new OAuthService();
        UserAccountGateway userAccountGateway = mock(UserAccountGateway.class);
        OAuthProviderRegistry providerRegistry = mock(OAuthProviderRegistry.class);
        OAuthStateTransactionService stateService =
                mock(OAuthStateTransactionService.class);
        OAuthStateClaimTransactionBoundary claimBoundary =
                mock(OAuthStateClaimTransactionBoundary.class);
        OAuthRemoteGateway remoteGateway = mock(OAuthRemoteGateway.class);
        AuthSessionService authSessionService = mock(AuthSessionService.class);

        ReflectionTestUtils.setField(
                service,
                "userAccountGateway",
                userAccountGateway);
        ReflectionTestUtils.setField(service, "providerRegistry", providerRegistry);
        ReflectionTestUtils.setField(service, "stateService", stateService);
        ReflectionTestUtils.setField(service, "claimBoundary", claimBoundary);
        ReflectionTestUtils.setField(service, "remoteGateway", remoteGateway);
        ReflectionTestUtils.setField(service, "authSessionService", authSessionService);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());

        OAuthStateContext context = new OAuthStateContext();
        context.setStateId(1L);
        context.setProvider("github");
        context.setPurpose("LOGIN");
        context.setCodeHash("code-hash");
        when(claimBoundary.claim("github", "state", "code"))
                .thenReturn(context);
        when(remoteGateway.exchange("github", "code"))
                .thenReturn(profile());
        when(remoteGateway.resolve(any()))
                .thenReturn(ApiResponse.success(loginReady()));
        return new Fixture(
                service,
                userAccountGateway,
                stateService,
                authSessionService);
    }

    private UserAccountSnapshotVO account(boolean membershipReady) {
        UserAccountSnapshotVO account = new UserAccountSnapshotVO();
        account.setUserId(7L);
        account.setMembershipReady(membershipReady);
        return account;
    }

    private OAuthProfile profile() {
        OAuthProfile profile = new OAuthProfile();
        profile.setProvider("github");
        profile.setProviderSubject("github-7");
        profile.setProviderUsername("alice");
        profile.setDisplayName("Alice");
        profile.setVerifiedEmail("alice@example.com");
        profile.setEmailVerified(true);
        return profile;
    }

    private SocialIdentityResolveResultVO loginReady() {
        SocialLoginPrincipalVO principal = new SocialLoginPrincipalVO();
        principal.setUserId(7L);
        principal.setUsername("alice");
        principal.setNickname("Alice");
        principal.setRole(2);
        return SocialIdentityResolveResultVO.loginReady(principal, null);
    }

    private static final class Fixture {
        private final OAuthService service;
        private final UserAccountGateway userAccountGateway;
        private final OAuthStateTransactionService stateService;
        private final AuthSessionService authSessionService;

        private Fixture(
                OAuthService service,
                UserAccountGateway userAccountGateway,
                OAuthStateTransactionService stateService,
                AuthSessionService authSessionService) {
            this.service = service;
            this.userAccountGateway = userAccountGateway;
            this.stateService = stateService;
            this.authSessionService = authSessionService;
        }
    }
}
