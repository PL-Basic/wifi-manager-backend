package com.plagod.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.client.UserSocialIdentityClient;
import com.plagod.constant.OAuthProvider;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.OAuthProfile;
import com.plagod.dto.OAuthStateContext;
import com.plagod.exception.ApiStatusException;
import com.plagod.service.oauth.OAuthProviderAdapter;
import com.plagod.service.oauth.OAuthProviderRegistry;
import com.plagod.vo.user.SocialIdentityResolveResultVO;
import com.plagod.vo.user.SocialLoginPrincipalVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthServiceSessionOrderingTest {

    @Test
    void oauthStateIsNotCompletedWhenRefreshSessionCreationFails() {
        OAuthService service = new OAuthService();
        DefaultTenantMembershipOutboxService outboxService =
                mock(DefaultTenantMembershipOutboxService.class);
        OAuthProviderRegistry providerRegistry = mock(OAuthProviderRegistry.class);
        OAuthStateTransactionService stateService =
                mock(OAuthStateTransactionService.class);
        UserSocialIdentityClient userClient = mock(UserSocialIdentityClient.class);
        AuthSessionService authSessionService = mock(AuthSessionService.class);
        OAuthProviderAdapter adapter = mock(OAuthProviderAdapter.class);

        ReflectionTestUtils.setField(
                service,
                "defaultTenantMembershipOutboxService",
                outboxService);
        ReflectionTestUtils.setField(service, "providerRegistry", providerRegistry);
        ReflectionTestUtils.setField(service, "stateService", stateService);
        ReflectionTestUtils.setField(service, "userClient", userClient);
        ReflectionTestUtils.setField(service, "authSessionService", authSessionService);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(
                service,
                "internalToken",
                "test-internal-token-value");

        OAuthStateContext context = new OAuthStateContext();
        context.setStateId(1L);
        context.setProvider("github");
        context.setPurpose("LOGIN");
        context.setCodeHash("code-hash");
        when(stateService.claim("github", "state", "code")).thenReturn(context);
        when(providerRegistry.require("github")).thenReturn(adapter);
        when(adapter.provider()).thenReturn(OAuthProvider.GITHUB);
        when(adapter.exchange("code")).thenReturn(profile());
        when(userClient.resolve(anyString(), any()))
                .thenReturn(ApiResponse.success(loginReady()));
        when(outboxService.isMembershipReady(7L)).thenReturn(true);
        when(authSessionService.open(
                any(),
                anyString(),
                anyString(),
                anyString())).thenThrow(
                ApiStatusException.serviceUnavailable("租户上下文服务暂时不可用"));

        assertThrows(
                ApiStatusException.class,
                () -> service.callback(
                        "github",
                        "state",
                        "code",
                        "client-instance",
                        "test-agent",
                        "192.168.1.23"));

        verify(stateService, never()).complete(
                any(),
                anyString(),
                any(),
                anyString());
        verify(stateService).fail(any(), anyString());
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
}
