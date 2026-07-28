package com.plagod.ws;

import com.plagod.utils.JwtUtils;
import io.jsonwebtoken.Claims;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AlertWebSocketHandshakeInterceptor
        implements HandshakeInterceptor {

    private static final String GATEWAY_TOKEN_HEADER = "X-Gateway-Token";

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final String USER_NAME_HEADER = "X-User-Name";

    private static final String USER_ROLE_HEADER = "X-User-Role";

    private static final String ORIGIN_HEADER = "Origin";

    private static final String PROTOCOL_HEADER = "Sec-WebSocket-Protocol";

    @Autowired
    private JwtUtils jwtUtils;

    @Value("${wifi.security.gateway-token}")
    private String expectedGatewayToken;

    @Value("${wifi.websocket.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    @Value("${wifi.websocket.allowed-origin-alt:http://127.0.0.1:5173}")
    private String allowedOriginAlt;

    private Set<String> allowedOrigins;

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(expectedGatewayToken) || expectedGatewayToken.getBytes(StandardCharsets.UTF_8).length < 16) {

            throw new IllegalStateException("WebSocket Gateway Token 必须配置且不能少于 16 字节");
        }

        allowedOrigins = new HashSet<>();

        addAllowedOrigin(allowedOrigin);
        addAllowedOrigin(allowedOriginAlt);

        if (allowedOrigins.isEmpty()) {
            throw new IllegalStateException("WebSocket 至少需要配置一个允许的 Origin");
        }
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {

        if (!isAllowedOrigin(request.getHeaders().getFirst(ORIGIN_HEADER))) {

            return reject(response, HttpStatus.FORBIDDEN);
        }

        String suppliedGatewayToken = request.getHeaders().getFirst(GATEWAY_TOKEN_HEADER);

        if (!constantTimeEquals(suppliedGatewayToken, expectedGatewayToken)) {

            return reject(response, HttpStatus.UNAUTHORIZED);
        }

        String jwt = extractProtocolToken(request);

        if (!StringUtils.hasText(jwt)) {
            return reject(response, HttpStatus.UNAUTHORIZED);
        }

        try {
            Claims claims = jwtUtils.parseToken(jwt);

            Long jwtUserId = JwtUtils.getUserId(claims);
            String jwtUsername = claims.get("username", String.class);
            Integer jwtRole = parseInteger(claims.get("role"));

            Long headerUserId = parseLong(request.getHeaders().getFirst(USER_ID_HEADER));

            String headerUsername = request.getHeaders().getFirst(USER_NAME_HEADER);

            Integer headerRole = parseInteger(request.getHeaders().getFirst(USER_ROLE_HEADER));

            if (jwtUserId == null || jwtUserId <= 0 || !StringUtils.hasText(jwtUsername) || !isAdmin(jwtRole)) {

                return reject(response, HttpStatus.FORBIDDEN);
            }

            // JWT 与 Gateway 注入身份必须完全一致。
            if (!jwtUserId.equals(headerUserId) || !jwtUsername.equals(headerUsername) || !jwtRole.equals(headerRole)) {

                return reject(response, HttpStatus.UNAUTHORIZED);
            }

            attributes.put("userId", jwtUserId);
            attributes.put("username", jwtUsername);
            attributes.put("role", jwtRole);

            return true;
        } catch (Exception exception) {
            return reject(response, HttpStatus.UNAUTHORIZED);
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // 握手后不需要额外处理。？？
    }

    private String extractProtocolToken(ServerHttpRequest request) {

        List<String> protocols = new ArrayList<>();

        for (String header : request.getHeaders().getOrEmpty(PROTOCOL_HEADER)) {

            for (String item : header.split(",")) {
                if (StringUtils.hasText(item)) {
                    protocols.add(item.trim());
                }
            }
        }

        for (int index = 0; index + 1 < protocols.size(); index++) {

            if ("access_token".equals(protocols.get(index))) {

                return protocols.get(index + 1);
            }
        }

        return null;
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

    private boolean reject(ServerHttpResponse response, HttpStatus status) {

        response.setStatusCode(status);
        return false;
    }

    private void addAllowedOrigin(String origin) {
        if (StringUtils.hasText(origin)) {
            allowedOrigins.add(origin.trim());
        }
    }
}