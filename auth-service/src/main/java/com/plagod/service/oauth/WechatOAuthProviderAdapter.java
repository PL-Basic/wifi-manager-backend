package com.plagod.service.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.plagod.configuration.OAuthProperties;
import com.plagod.constant.OAuthProvider;
import com.plagod.dto.OAuthProfile;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class WechatOAuthProviderAdapter implements OAuthProviderAdapter {

    private final RestTemplate restTemplate;
    private final OAuthProperties.Provider config;

    public WechatOAuthProviderAdapter(@Qualifier("oauthRestTemplate") RestTemplate restTemplate, OAuthProperties properties) {

        this.restTemplate = restTemplate;
        this.config = properties.getWechat();
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.WECHAT;
    }

    @Override
    public boolean isAvailable() {
        return hasRequiredConfiguration();
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        requireAvailable();
        requireText(state, "state");

        return UriComponentsBuilder
                .fromHttpUrl(config.getAuthorizationUri())
                .queryParam("appid", config.getClientId())
                .queryParam("redirect_uri", config.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", config.getScope())
                .queryParam("state", state)
                .fragment("wechat_redirect")
                .build()
                .encode()
                .toUriString();
    }

    @Override
    public OAuthProfile exchange(String authorizationCode) {
        requireAvailable();
        requireText(authorizationCode, "authorizationCode");

        try {
            JsonNode token = exchangeToken(authorizationCode);

            String accessToken = requiredText(token, "access_token");
            String openId = requiredText(token, "openid");

            JsonNode user = loadUser(accessToken, openId);

            String unionId = text(user, "unionid");
            if (!StringUtils.hasText(unionId)) {
                unionId = text(token, "unionid");
            }

            OAuthProfile profile = new OAuthProfile();
            profile.setProvider(provider().value());
            profile.setProviderSubject(openId);
            profile.setProviderUnionId(unionId);
            profile.setDisplayName(text(user, "nickname"));
            profile.setAvatarUrl(text(user, "headimgurl"));
            profile.setEmailVerified(false);
            return profile;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("微信 OAuth 服务暂时不可用");
        }
    }

    private JsonNode exchangeToken(String code) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl(config.getTokenUri())
                .queryParam("appid", config.getClientId())
                .queryParam("secret", config.getClientSecret())
                .queryParam("code", code)
                .queryParam("grant_type", "authorization_code")
                .build()
                .encode()
                .toUri();

        JsonNode token = restTemplate.getForObject(uri, JsonNode.class);

        rejectProviderError(token, "微信 token 交换失败");
        return token;
    }

    private JsonNode loadUser(String accessToken, String openId) {

        URI uri = UriComponentsBuilder
                .fromHttpUrl(config.getUserInfoUri())
                .queryParam("access_token", accessToken)
                .queryParam("openid", openId)
                .queryParam("lang", "zh_CN")
                .build()
                .encode()
                .toUri();

        JsonNode user = restTemplate.getForObject(uri, JsonNode.class);

        rejectProviderError(user, "微信用户资料读取失败");

        String returnedOpenId =
                requiredText(user, "openid");
        if (!openId.equals(returnedOpenId)) {
            throw new IllegalStateException("微信用户资料 openid 不一致");
        }

        return user;
    }

    private void rejectProviderError(JsonNode response, String message) {

        if (response == null || !response.isObject() || response.has("errcode")) {
            throw new IllegalStateException(message);
        }
    }

    private String requiredText(JsonNode node, String field) {

        String value = text(node, field);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("微信响应缺少 " + field);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }

        String text = value.asText();
        return StringUtils.hasText(text) ? text : null;
    }

    private boolean hasRequiredConfiguration() {
        return StringUtils.hasText(config.getClientId()) &&
                StringUtils.hasText(config.getClientSecret()) &&
                StringUtils.hasText(config.getRedirectUri()) &&
                StringUtils.hasText(config.getAuthorizationUri()) &&
                StringUtils.hasText(config.getTokenUri()) &&
                StringUtils.hasText(config.getUserInfoUri());
    }

    private void requireAvailable() {
        if (!hasRequiredConfiguration()) {
            throw new IllegalStateException("微信 OAuth 当前未配置");
        }
    }

    private void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + "不能为空");
        }
    }
}