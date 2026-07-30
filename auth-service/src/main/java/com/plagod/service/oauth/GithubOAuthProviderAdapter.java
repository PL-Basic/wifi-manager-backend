package com.plagod.service.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.plagod.configuration.OAuthProperties;
import com.plagod.constant.OAuthProvider;
import com.plagod.dto.OAuthProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;

@Slf4j
@Component
public class GithubOAuthProviderAdapter implements OAuthProviderAdapter {

    private final RestTemplate restTemplate;
    private final OAuthProperties.Provider config;

    public GithubOAuthProviderAdapter(@Qualifier("oauthRestTemplate") RestTemplate restTemplate, OAuthProperties properties) {

        this.restTemplate = restTemplate;
        this.config = properties.getGithub();
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.GITHUB;
    }

    @Override
    public boolean isAvailable() {
        return hasRequiredConfiguration();
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        requireAvailable();
        requireCodeOrState(state, "state");

        return UriComponentsBuilder
                .fromHttpUrl(config.getAuthorizationUri())
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
        requireCodeOrState(authorizationCode, "authorizationCode");

        try {
            String accessToken = exchangeAccessToken(authorizationCode);

            JsonNode user = loadUser(accessToken);
            String verifiedEmail = loadVerifiedEmail(accessToken);

            JsonNode idNode = user.get("id");
            if (idNode == null || idNode.isNull()) {
                throw new IllegalStateException("GitHub 用户资料缺少稳定 ID");
            }

            OAuthProfile profile = new OAuthProfile();
            profile.setProvider(provider().value());
            profile.setProviderSubject(idNode.asText());
            profile.setProviderUsername(text(user, "login"));
            profile.setDisplayName(text(user, "name"));
            profile.setAvatarUrl(text(user, "avatar_url"));
            profile.setVerifiedEmail(verifiedEmail);
            profile.setEmailVerified(StringUtils.hasText(verifiedEmail));
            return profile;
        } catch (RestClientException exception) {
            throw new IllegalStateException("GitHub OAuth 服务暂时不可用");
        }
    }

    private String exchangeAccessToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", config.getClientId());
        form.add("client_secret", config.getClientSecret());
        form.add("code", code);
        form.add("redirect_uri", config.getRedirectUri());

        ResponseEntity<JsonNode> response = restTemplate.exchange(config.getTokenUri(), HttpMethod.POST, new HttpEntity<>(form, headers), JsonNode.class);

        JsonNode body = response.getBody();
        String token = text(body, "access_token");

        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("GitHub 没有返回有效 access_token");
        }
        return token;
    }

    private JsonNode loadUser(String accessToken) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(config.getUserInfoUri(), HttpMethod.GET, bearerRequest(accessToken), JsonNode.class);

        JsonNode body = response.getBody();
        if (body == null || !body.isObject()) {
            throw new IllegalStateException("GitHub 用户资料响应无效");
        }
        return body;
    }

    private String loadVerifiedEmail(String accessToken) {
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(config.getEmailUri(), HttpMethod.GET, bearerRequest(accessToken), JsonNode.class);

            JsonNode emails = response.getBody();
            if (emails == null || !emails.isArray()) {
                return null;
            }

            String firstVerified = null;
            for (JsonNode email : emails) {
                if (!email.path("verified").asBoolean(false)) {
                    continue;
                }

                String value = text(email, "email");
                if (!StringUtils.hasText(value)) {
                    continue;
                }

                if (email.path("primary").asBoolean(false)) {
                    return value;
                }
                if (firstVerified == null) {
                    firstVerified = value;
                }
            }
            return firstVerified;
        } catch (RestClientException exception) {

            log.info("GitHub 用户资料可用，但未能读取验证邮箱");
            return null;
        }
    }

    private HttpEntity<Void> bearerRequest(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(headers);
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }

        JsonNode value = node.get(field);
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
                StringUtils.hasText(config.getUserInfoUri()) &&
                StringUtils.hasText(config.getEmailUri());
    }

    private void requireAvailable() {
        if (!hasRequiredConfiguration()) {
            throw new IllegalStateException("GitHub OAuth 当前未配置");
        }
    }

    private void requireCodeOrState(String value, String fieldName) {

        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
    }
}