package com.plagod.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Data
@ConfigurationProperties(prefix = "wifi.security")
public class TrustedRequestProperties {

    private boolean enabled = true;

    private String gatewayToken;

    private String internalToken;

    private boolean internalTokenRequired;

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

    public void validateProduction(String outboundInternalToken) {
        if (!enabled) {
            throw new IllegalStateException("生产环境不能关闭可信请求认证");
        }

        validateProductionToken(gatewayToken, "Gateway Token");
        validateProductionToken(outboundInternalToken, "出站 Internal Token");

        if (constantTimeMatches(gatewayToken, outboundInternalToken)) {
            throw new IllegalStateException("Gateway Token 与 Internal Token 不能使用相同值");
        }

        if (internalTokenRequired) {
            validateProductionToken(internalToken, "入站 Internal Token");

            if (!constantTimeMatches(internalToken, outboundInternalToken)) {
                throw new IllegalStateException("入站与出站 Internal Token 必须使用相同值");
            }
        } else if (StringUtils.hasText(internalToken)) {
            throw new IllegalStateException("当前服务没有内部入站接口，不能启用入站 Internal Token");
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
        if (!StringUtils.hasText(token) || token.getBytes(StandardCharsets.UTF_8).length < 16) {
            throw new IllegalStateException(name + " 必须配置且不能少于 16 字节");
        }
    }

    private void validateProductionToken(String token, String name) {
        validateToken(token, name);

        if (!token.equals(token.trim())) {
            throw new IllegalStateException(name + " 首尾不能包含空白字符");
        }

        String upperToken = token.toUpperCase(Locale.ROOT);
        if (upperToken.contains("CHANGE_ME")
                || upperToken.contains("CHANGE-ME")
                || upperToken.contains("LOCAL-GATEWAY-TOKEN")
                || upperToken.contains("LOCAL-INTERNAL-TOKEN")) {
            throw new IllegalStateException(name + " 仍是示例值，生产环境拒绝启动");
        }
    }

    private boolean constantTimeMatches(String first, String second) {
        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.UTF_8),
                second.getBytes(StandardCharsets.UTF_8));
    }
}
