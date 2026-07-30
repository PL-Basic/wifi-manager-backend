package com.plagod.service.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.configuration.OAuthProperties;
import com.plagod.constant.OAuthProvider;
import com.plagod.dto.OAuthProfile;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class QqOAuthProviderAdapter implements OAuthProviderAdapter {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OAuthProperties.Provider config;

    public QqOAuthProviderAdapter(@Qualifier("oauthRestTemplate") RestTemplate restTemplate, ObjectMapper objectMapper, OAuthProperties properties) {

        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.config = properties.getQq();
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.QQ;
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
                .queryParam("response_type", "code")
                .queryParam("client_id", config.getClientId())
                .queryParam("redirect_uri", config.getRedirectUri())
                .queryParam("scope", config.getScope())
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    @Override
    public OAuthProfile exchange(String authorizationCode) {
        requireAvailable();
        requireText(authorizationCode, "authorizationCode");

        try {
            String accessToken = exchangeAccessToken(authorizationCode);
            String openId = loadOpenId(accessToken);
            JsonNode user = loadUser(accessToken, openId);

            OAuthProfile profile = new OAuthProfile();
            profile.setProvider(provider().value());
            profile.setProviderSubject(openId);
            profile.setDisplayName(text(user, "nickname"));

            String avatar = text(user, "figureurl_qq_2");
            if (!StringUtils.hasText(avatar)) {
                avatar = text(user, "figureurl_qq_1");
            }
            profile.setAvatarUrl(avatar);
            profile.setEmailVerified(false);
            return profile;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("QQ OAuth 服务暂时不可用");
        }
    }

    private String exchangeAccessToken(String code) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl(config.getTokenUri())
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", config.getClientId())
                .queryParam("client_secret", config.getClientSecret())
                .queryParam("code", code)
                .queryParam("redirect_uri", config.getRedirectUri())
                .queryParam("fmt", "json")
                .build()
                .encode()
                .toUri();

        String body = restTemplate.getForObject(uri, String.class);

        if (!StringUtils.hasText(body)) {
            throw new IllegalStateException("QQ 没有返回 token 响应");
        }

        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            JsonNode json = readJson(trimmed);
            String token = text(json, "access_token");
            if (!StringUtils.hasText(token)) {
                throw new IllegalStateException("QQ 没有返回有效 access_token");
            }
            return token;
        }

        MultiValueMap<String, String> values = UriComponentsBuilder.fromUriString("http://localhost/?" + trimmed).build().getQueryParams();

        String token = values.getFirst("access_token");
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("QQ 没有返回有效 access_token");
        }
        return token;
    }

    private String loadOpenId(String accessToken) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl(config.getOpenIdUri())
                .queryParam("access_token", accessToken)
                .queryParam("fmt", "json")
                .build()
                .encode()
                .toUri();

        String body = restTemplate.getForObject(uri, String.class);

        if (!StringUtils.hasText(body)) {
            throw new IllegalStateException("QQ 没有返回 openid");
        }

        int firstBrace = body.indexOf('{');
        int lastBrace = body.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace < firstBrace) {
            throw new IllegalStateException("QQ openid 响应格式无效");
        }

        JsonNode json = readJson(body.substring(firstBrace, lastBrace + 1));
        String openId = text(json, "openid");

        if (!StringUtils.hasText(openId)) {
            throw new IllegalStateException("QQ 没有返回有效 openid");
        }
        return openId;
    }

    private JsonNode loadUser(String accessToken, String openId) {

        URI uri = UriComponentsBuilder
                .fromHttpUrl(config.getUserInfoUri())
                .queryParam("access_token", accessToken)
                .queryParam("oauth_consumer_key", config.getClientId())
                .queryParam("openid", openId)
                .build()
                .encode()
                .toUri();

        JsonNode user = restTemplate.getForObject(uri, JsonNode.class);

        if (user == null || user.path("ret").asInt(-1) != 0) {
            throw new IllegalStateException("QQ 用户资料响应无效");
        }
        return user;
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException("QQ OAuth 响应不是有效 JSON");
        }
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
                StringUtils.hasText(config.getOpenIdUri()) &&
                StringUtils.hasText(config.getUserInfoUri());
    }

    private void requireAvailable() {
        if (!hasRequiredConfiguration()) {
            throw new IllegalStateException("QQ OAuth 当前未配置");
        }
    }

    private void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + "不能为空");
        }
    }
}