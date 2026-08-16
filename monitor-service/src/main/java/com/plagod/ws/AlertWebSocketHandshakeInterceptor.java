package com.plagod.ws;

import com.plagod.configuration.AlertWebSocketProperties;
import com.plagod.support.SafeConfigurationValue;
import com.plagod.support.StructuredRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class AlertWebSocketHandshakeInterceptor
        implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AlertWebSocketHandshakeInterceptor.class);

    private static final String GATEWAY_TOKEN_HEADER = "X-Gateway-Token";

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final String USER_NAME_HEADER = "X-User-Name";

    private static final String USER_ROLE_HEADER = "X-User-Role";

    private static final String ORIGIN_HEADER = "Origin";

    private static final String PROTOCOL_HEADER = "Sec-WebSocket-Protocol";

    private static final Set<String> SAFE_REJECTION_LOG_KEYS;

    private static final Set<String> REDACTED_REJECTION_LOG_KEYS;

    static {
        Set<String> keys = new HashSet<>();
        Collections.addAll(
                keys,
                "reason",
                "status",
                "path",
                "origin",
                "gatewayTokenPresent",
                "accessTokenProtocolPresent",
                "identityHeadersPresent",
                "trustedUserId",
                "trustedRole");
        SAFE_REJECTION_LOG_KEYS = Collections.unmodifiableSet(keys);

        Set<String> redactedKeys = new HashSet<>();
        Collections.addAll(redactedKeys, "path", "origin");
        REDACTED_REJECTION_LOG_KEYS =
                Collections.unmodifiableSet(redactedKeys);
    }

    @Value("${wifi.security.gateway-token}")
    private String expectedGatewayToken;

    @Autowired
    private AlertWebSocketProperties webSocketProperties;

    private Set<String> allowedOrigins;

    @PostConstruct
    public void init() {
        expectedGatewayToken = SafeConfigurationValue.requireSecret(
                "wifi.security.gateway-token",
                expectedGatewayToken,
                1,
                Collections.<String>emptySet());
        if (expectedGatewayToken.getBytes(StandardCharsets.UTF_8).length
                < 16) {
            throw new IllegalStateException(
                    "wifi.security.gateway-token must contain at least "
                            + "16 UTF-8 bytes");
        }

        allowedOrigins = new HashSet<>();

        if (webSocketProperties.getAllowedOrigins() == null) {
            throw new IllegalStateException(
                    "wifi.websocket.allowed-origins must be configured");
        }
        for (String origin : webSocketProperties.getAllowedOrigins()) {
            addAllowedOrigin(SafeConfigurationValue.requireText(
                    "wifi.websocket.allowed-origins",
                    origin));
        }

        if (allowedOrigins.isEmpty()) {
            throw new IllegalStateException("WebSocket 至少需要配置一个允许的 Origin");
        }
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {

        if (!isAllowedOrigin(request.getHeaders().getFirst(ORIGIN_HEADER))) {

            return reject(response, HttpStatus.FORBIDDEN, "ORIGIN_NOT_ALLOWED", request, null, null);
        }

        String suppliedGatewayToken = request.getHeaders().getFirst(GATEWAY_TOKEN_HEADER);

        if (!constantTimeEquals(suppliedGatewayToken, expectedGatewayToken)) {

            return reject(response, HttpStatus.UNAUTHORIZED, "GATEWAY_TOKEN_MISMATCH", request, null, null);
        }

        if (!hasAccessTokenProtocol(request)) {
            return reject(response, HttpStatus.UNAUTHORIZED, "ACCESS_TOKEN_PROTOCOL_MISSING", request, null, null);
        }

        Long headerUserId;
        Integer headerRole;

        try {
            headerUserId = parseLong(request.getHeaders().getFirst(USER_ID_HEADER));
            headerRole = parseInteger(request.getHeaders().getFirst(USER_ROLE_HEADER));
        } catch (Exception exception) {
            return reject(response, HttpStatus.UNAUTHORIZED, "IDENTITY_HEADER_INVALID", request, null, null);
        }

        String headerUsername = request.getHeaders().getFirst(USER_NAME_HEADER);

        if (headerUserId == null || headerUserId <= 0 || !StringUtils.hasText(headerUsername) || headerRole == null) {
            return reject(response, HttpStatus.UNAUTHORIZED, "IDENTITY_HEADER_INVALID", request, headerUserId, headerRole);
        }

        if (!isAdmin(headerRole)) {
            return reject(response, HttpStatus.FORBIDDEN, "IDENTITY_ROLE_FORBIDDEN", request, headerUserId, headerRole);
        }

        // JWT 已由 Gateway 验证；这里只接收 Gateway 注入且经过服务凭据保护的身份。
        attributes.put("userId", headerUserId);
        attributes.put("username", headerUsername);
        attributes.put("role", headerRole);

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // 握手完成后的连接生命周期由 Handler 统一维护。
    }

    private boolean hasAccessTokenProtocol(ServerHttpRequest request) {
        for (String header : request.getHeaders().getOrEmpty(PROTOCOL_HEADER)) {
            for (String item : header.split(",")) {
                if ("access_token".equals(item.trim())) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isAllowedOrigin(String origin) {
        return StringUtils.hasText(origin) && allowedOrigins.contains(origin.trim());
    }

    private boolean isAdmin(Integer role) {
        return Integer.valueOf(0).equals(role) || Integer.valueOf(1).equals(role);
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }

        return Long.valueOf(String.valueOf(value));
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }

        return Integer.valueOf(String.valueOf(value));
    }

    private boolean constantTimeEquals(String supplied, String expected) {

        if (!StringUtils.hasText(supplied) || !StringUtils.hasText(expected)) {

            return false;
        }

        return MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private boolean reject(
            ServerHttpResponse response,
            HttpStatus status,
            String reason,
            ServerHttpRequest request,
            Long trustedUserId,
            Integer trustedRole) {

        response.setStatusCode(status);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("reason", reason);
        fields.put("status", status.value());
        fields.put("path", request.getURI().getPath());
        fields.put("origin", request.getHeaders().getFirst(ORIGIN_HEADER));
        fields.put(
                "gatewayTokenPresent",
                StringUtils.hasText(request.getHeaders().getFirst(
                        GATEWAY_TOKEN_HEADER)));
        fields.put(
                "accessTokenProtocolPresent",
                hasAccessTokenProtocol(request));
        fields.put("identityHeadersPresent", hasIdentityHeaders(request));
        fields.put("trustedUserId", trustedUserId);
        fields.put("trustedRole", trustedRole);

        log.warn(
                "alert websocket handshake rejected: context={}",
                StructuredRedactor.redact(
                        fields,
                        SAFE_REJECTION_LOG_KEYS,
                        REDACTED_REJECTION_LOG_KEYS));

        return false;
    }

    private boolean hasIdentityHeaders(ServerHttpRequest request) {
        return StringUtils.hasText(request.getHeaders().getFirst(USER_ID_HEADER))
                && StringUtils.hasText(request.getHeaders().getFirst(USER_NAME_HEADER))
                && StringUtils.hasText(request.getHeaders().getFirst(USER_ROLE_HEADER));
    }

    private void addAllowedOrigin(String origin) {
        if (StringUtils.hasText(origin)) {
            allowedOrigins.add(origin.trim());
        }
    }
}
