package com.plagod.service.oauth;

import com.plagod.constant.OAuthProvider;
import com.plagod.dto.OAuthProfile;

public interface OAuthProviderAdapter {

    OAuthProvider provider();

    boolean isAvailable();

    String buildAuthorizationUrl(String state);

    OAuthProfile exchange(String authorizationCode);
}