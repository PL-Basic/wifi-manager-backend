package com.plagod.service;

import com.plagod.client.UserSocialIdentityClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.OAuthProfile;
import com.plagod.dto.user.SocialIdentityResolveDTO;
import com.plagod.service.oauth.OAuthProviderRegistry;
import com.plagod.vo.user.SocialIdentityResolveResultVO;
import com.plagod.vo.user.SocialLoginPrincipalVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OAuthRemoteGateway {

    private final OAuthProviderRegistry providerRegistry;
    private final UserSocialIdentityClient userClient;
    private final String internalToken;
    private final TransactionTemplate remoteCallTemplate;

    public OAuthRemoteGateway(
            OAuthProviderRegistry providerRegistry,
            UserSocialIdentityClient userClient,
            PlatformTransactionManager transactionManager,
            @Value("${wifi.internal.token}") String internalToken) {
        this.providerRegistry = providerRegistry;
        this.userClient = userClient;
        this.internalToken = internalToken;
        this.remoteCallTemplate = new TransactionTemplate(transactionManager);
        this.remoteCallTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    public OAuthProfile exchange(
            String provider,
            String authorizationCode) {
        return remoteCallTemplate.execute(status ->
                providerRegistry.require(provider)
                        .exchange(authorizationCode));
    }

    public ApiResponse<SocialIdentityResolveResultVO> resolve(
            SocialIdentityResolveDTO request) {
        return remoteCallTemplate.execute(status ->
                userClient.resolve(internalToken, request));
    }

    public ApiResponse<SocialLoginPrincipalVO> getPrincipal(Long userId) {
        return remoteCallTemplate.execute(status ->
                userClient.getPrincipal(internalToken, userId));
    }
}
