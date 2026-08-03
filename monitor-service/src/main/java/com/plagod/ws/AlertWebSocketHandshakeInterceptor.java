package com.plagod.ws;

import com.plagod.configuration.AlertWebSocketProperties;
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
import java.util.HashSet;
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

    @Value("${wifi.security.gateway-token}")
    private String expectedGatewayToken;

    @Autowired
    private AlertWebSocketProperties webSocketProperties;

    private Set<String> allowedOrigins;

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(expectedGatewayToken) || expectedGatewayToken.getBytes(StandardCharsets.UTF_8).length < 16) {

            throw new IllegalStateException("WebSocket Gateway Token 必须配置且不能少于 16 字节");
        }

        allowedOrigins = new HashSet<>();

        for (String origin : webSocketProperties.getAllowedOrigins()) {
            addAllowedOrigin(origin);
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

        log.warn(
                "alert websocket handshake rejected: reason={}, status={}, path={}, origin={}, gatewayTokenPresent={}, accessTokenProtocolPresent={}, identityHeadersPresent={}, trustedUserId={}, trustedRole={}",
                reason,
                status.value(),
                request.getURI().getPath(),
                safeLogValue(request.getHeaders().getFirst(ORIGIN_HEADER)),
                StringUtils.hasText(request.getHeaders().getFirst(GATEWAY_TOKEN_HEADER)),
                hasAccessTokenProtocol(request),
                hasIdentityHeaders(request),
                trustedUserId,
                trustedRole);

        return false;
    }

    private boolean hasIdentityHeaders(ServerHttpRequest request) {
        return StringUtils.hasText(request.getHeaders().getFirst(USER_ID_HEADER))
                && StringUtils.hasText(request.getHeaders().getFirst(USER_NAME_HEADER))
                && StringUtils.hasText(request.getHeaders().getFirst(USER_ROLE_HEADER));
    }

    private String safeLogValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "<missing>";
        }

        String normalized = value.replace('\r', '_').replace('\n', '_').trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    private void addAllowedOrigin(String origin) {
        if (StringUtils.hasText(origin)) {
            allowedOrigins.add(origin.trim());
        }
    }
}
