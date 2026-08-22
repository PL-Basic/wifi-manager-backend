package com.plagod.ws;

import com.plagod.configuration.AlertWebSocketProperties;
import com.plagod.security.TrustedContextType;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.TrustedRequestContextException;
import com.plagod.security.TrustedRequestContextResolver;
import com.plagod.security.TrustedRequestHeaders;
import com.plagod.security.TrustedSource;
import com.plagod.support.SafeConfigurationValue;
import com.plagod.support.StructuredRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
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
                "trustedUserId",
                "trustedTenantId",
                "trustedContextType");
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

    @Autowired
    private TrustedRequestContextResolver contextResolver;

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

            return reject(
                    response,
                    HttpStatus.FORBIDDEN,
                    "ORIGIN_NOT_ALLOWED",
                    request,
                    null);
        }

        String suppliedGatewayToken = request.getHeaders().getFirst(
                TrustedRequestHeaders.GATEWAY_TOKEN);

        if (!constantTimeEquals(suppliedGatewayToken, expectedGatewayToken)) {

            return reject(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "GATEWAY_TOKEN_MISMATCH",
                    request,
                    null);
        }

        if (!hasAccessTokenProtocol(request)) {
            return reject(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "ACCESS_TOKEN_PROTOCOL_MISSING",
                    request,
                    null);
        }

        if (!(request instanceof ServletServerHttpRequest)) {
            return reject(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "SERVLET_REQUEST_REQUIRED",
                    request,
                    null);
        }

        HttpServletRequest servletRequest =
                ((ServletServerHttpRequest) request).getServletRequest();
        TrustedRequestContext context;
        try {
            context = contextResolver.resolve(servletRequest);
        } catch (TrustedRequestContextException exception) {
            HttpStatus status = exception.getHttpStatus() == 403
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.UNAUTHORIZED;
            return reject(
                    response,
                    status,
                    status == HttpStatus.FORBIDDEN
                            ? "TRUSTED_CONTEXT_FORBIDDEN"
                            : "TRUSTED_CONTEXT_INVALID",
                    request,
                    null);
        }

        if (context == null) {
            return reject(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "TRUSTED_CONTEXT_MISSING",
                    request,
                    null);
        }

        if (context.getTrustedSource() != TrustedSource.GATEWAY_USER) {
            return reject(
                    response,
                    HttpStatus.FORBIDDEN,
                    "TRUSTED_SOURCE_FORBIDDEN",
                    request,
                    context);
        }

        if (!canSubscribe(context)) {
            return reject(
                    response,
                    HttpStatus.FORBIDDEN,
                    "TRUSTED_CONTEXT_NOT_SUBSCRIBER",
                    request,
                    context);
        }

        Long tenantId = parseTenantId(context.getTenantId());
        if (tenantId == null) {
            return reject(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "TRUSTED_TENANT_INVALID",
                    request,
                    context);
        }

        attributes.put(
                AlertWebSocketHandler.ATTRIBUTE_TENANT_ID,
                tenantId);
        attributes.put(
                AlertWebSocketHandler.ATTRIBUTE_USER_ID,
                context.getUserId());
        attributes.put(
                AlertWebSocketHandler.ATTRIBUTE_CONTEXT_TYPE,
                context.getContextType());

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

    private Long parseTenantId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean canSubscribe(TrustedRequestContext context) {
        if (context.getContextType() == TrustedContextType.TENANT) {
            return "TENANT_ADMIN".equals(context.getTenantRole());
        }
        return context.getContextType()
                == TrustedContextType.PLATFORM_TENANT;
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
            TrustedRequestContext context) {

        response.setStatusCode(status);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("reason", reason);
        fields.put("status", status.value());
        fields.put("path", request.getURI().getPath());
        fields.put("origin", request.getHeaders().getFirst(ORIGIN_HEADER));
        fields.put(
                "gatewayTokenPresent",
                StringUtils.hasText(request.getHeaders().getFirst(
                        TrustedRequestHeaders.GATEWAY_TOKEN)));
        fields.put(
                "accessTokenProtocolPresent",
                hasAccessTokenProtocol(request));
        fields.put(
                "trustedUserId",
                context == null ? null : context.getUserId());
        fields.put(
                "trustedTenantId",
                context == null ? null : context.getTenantId());
        fields.put(
                "trustedContextType",
                context == null ? null : context.getContextType());

        log.warn(
                "alert websocket handshake rejected: context={}",
                StructuredRedactor.redact(
                        fields,
                        SAFE_REJECTION_LOG_KEYS,
                        REDACTED_REJECTION_LOG_KEYS));

        return false;
    }

    private void addAllowedOrigin(String origin) {
        if (StringUtils.hasText(origin)) {
            allowedOrigins.add(origin.trim());
        }
    }
}
