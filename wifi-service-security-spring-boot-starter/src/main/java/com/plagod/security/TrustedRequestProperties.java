package com.plagod.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "wifi.security")
public class TrustedRequestProperties {

    private boolean enabled = true;

    private String gatewayToken;

    private String internalToken;

    private List<String> internalPathPrefixes =
            new ArrayList<>(Collections.singletonList("/internal/"));

    @PostConstruct
    public void validate() {
        if (!enabled) {
            return;
        }

        validateToken(gatewayToken, "Gateway Token");

        if (StringUtils.hasText(internalToken)) {
            validateToken(internalToken, "Internal Token");

            if (constantTimeMatches(gatewayToken, internalToken)) {
                throw new IllegalStateException("Gateway Token 与 Internal Token 不能使用相同值");
            }
        }
    }

    public boolean isInternalPath(String path) {
        if (!StringUtils.hasText(path) || internalPathPrefixes == null) {
            return false;
        }

        for (String prefix : internalPathPrefixes) {
            if (StringUtils.hasText(prefix) && path.startsWith(prefix.trim())) {
                return true;
            }
        }

        return false;
    }

    private void validateToken(String token, String name) {
        if (!StringUtils.hasText(token) || token.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 16) {
            throw new IllegalStateException(name + " 必须配置且不能少于 16 字节");
        }
    }

    private boolean constantTimeMatches(String first, String second) {
        return java.security.MessageDigest.isEqual(first.getBytes(java.nio.charset.StandardCharsets.UTF_8), second.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }
}