package com.plagod.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "oauth")
public class OAuthProperties {

    private int stateExpireMinutes = 10;
    private int connectTimeoutMillis = 5000;
    private int readTimeoutMillis = 8000;

    private List<String> allowedReturnOrigins =
            Arrays.asList("http://localhost:5173");

    private Provider github = githubDefaults();
    private Provider qq = qqDefaults();
    private Provider wechat = wechatDefaults();

    @Data
    public static class Provider {

        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String scope;

        private String authorizationUri;
        private String tokenUri;
        private String userInfoUri;
        private String emailUri;
        private String openIdUri;
    }

    private static Provider githubDefaults() {
        Provider provider = new Provider();
        provider.setClientId("");
        provider.setClientSecret("");
        provider.setRedirectUri("http://localhost:8080/auth/oauth/github/callback");
        provider.setScope("read:user user:email");
        provider.setAuthorizationUri("https://github.com/login/oauth/authorize");
        provider.setTokenUri("https://github.com/login/oauth/access_token");
        provider.setUserInfoUri("https://api.github.com/user");
        provider.setEmailUri("https://api.github.com/user/emails");
        return provider;
    }

    private static Provider qqDefaults() {
        Provider provider = new Provider();
        provider.setClientId("");
        provider.setClientSecret("");
        provider.setRedirectUri("http://localhost:8080/auth/oauth/qq/callback");
        provider.setScope("get_user_info");
        provider.setAuthorizationUri("https://graph.qq.com/oauth2.0/authorize");
        provider.setTokenUri("https://graph.qq.com/oauth2.0/token");
        provider.setOpenIdUri("https://graph.qq.com/oauth2.0/me");
        provider.setUserInfoUri("https://graph.qq.com/user/get_user_info");
        return provider;
    }

    private static Provider wechatDefaults() {
        Provider provider = new Provider();
        provider.setClientId("");
        provider.setClientSecret("");
        provider.setRedirectUri("http://localhost:8080/auth/oauth/wechat/callback");
        provider.setScope("snsapi_login");
        provider.setAuthorizationUri("https://open.weixin.qq.com/connect/qrconnect");
        provider.setTokenUri("https://api.weixin.qq.com/sns/oauth2/access_token");
        provider.setUserInfoUri("https://api.weixin.qq.com/sns/userinfo");
        return provider;
    }
}