package com.plagod.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.security.TrustedContextType;
import com.plagod.web.SafeExceptionLogFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AlertWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {

    private static final Logger log = LoggerFactory.getLogger(AlertWebSocketHandler.class);

    static final String ATTRIBUTE_TENANT_ID =
            AlertWebSocketHandler.class.getName() + ".tenantId";
    static final String ATTRIBUTE_USER_ID =
            AlertWebSocketHandler.class.getName() + ".userId";
    static final String ATTRIBUTE_CONTEXT_TYPE =
            AlertWebSocketHandler.class.getName() + ".contextType";

    private static final String ACCESS_TOKEN_PROTOCOL = "access_token";

    private static final int SEND_TIME_LIMIT_MILLIS = 10_000;

    private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

    private static final long HEARTBEAT_TIMEOUT_MILLIS = 75_000L;

    private static final CloseStatus HEARTBEAT_TIMEOUT_STATUS =
            new CloseStatus(4000, "Heartbeat timeout");

    private final Map<String, TenantConnection> sessions =
            new ConcurrentHashMap<>();

    private final Map<String, Long> lastPongTimes = new ConcurrentHashMap<>();

    /**
     * 使用 Spring Boot 配置好的 ObjectMapper，
     * 保留 LocalDateTime 等类型的序列化模块。
     */
    private final ObjectMapper objectMapper;

    public AlertWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 浏览器提交 access_token 和 JWT 两个协议值时，
     * 服务端只选择固定的 access_token 子协议。
     */
    @Override
    public List<String> getSubProtocols() {
        return Collections.singletonList(ACCESS_TOKEN_PROTOCOL);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        TenantBinding binding = readBinding(session);
        if (binding == null) {
            removeConnection(session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        WebSocketSession concurrentSession =
                new ConcurrentWebSocketSessionDecorator(
                        session,
                        SEND_TIME_LIMIT_MILLIS,
                        SEND_BUFFER_LIMIT_BYTES);
        TenantConnection connection = new TenantConnection(
                binding.tenantId,
                binding.userId,
                binding.contextType,
                concurrentSession);

        sessions.put(session.getId(), connection);
        lastPongTimes.put(session.getId(), System.currentTimeMillis());

        log.info(
                "alert websocket connected: sessionId={}, userId={}, tenantId={}, contextType={}, total={}",
                session.getId(),
                connection.userId,
                connection.tenantId,
                connection.contextType,
                sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

        removeConnection(session.getId());

        log.info("alert websocket closed: sessionId={}, status={}, total={}", session.getId(), status, sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {

        removeConnection(session.getId());

        log.warn(
                "alert websocket transport error: sessionId={}, safeStack={}",
                session.getId(),
                SafeExceptionLogFormatter.format(exception));

        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    public void broadcastToTenant(Long tenantId, Object payload) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("告警广播缺少有效租户身份");
        }

        boolean targetPresent = sessions.values().stream()
                .anyMatch(connection ->
                        tenantId.equals(connection.tenantId));
        if (!targetPresent) {
            return;
        }

        String json;

        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            log.warn(
                    "alert websocket serialization failed: safeStack={}",
                    SafeExceptionLogFormatter.format(exception));
            return;
        }

        TextMessage message = new TextMessage(json);

        for (TenantConnection connection : sessions.values()) {
            if (!tenantId.equals(connection.tenantId)) {
                continue;
            }
            WebSocketSession session = connection.session;
            if (!session.isOpen()) {
                removeConnection(session.getId());
                continue;
            }

            try {
                // ConcurrentWebSocketSessionDecorator
                // 会串行化同一连接上的并发发送。
                session.sendMessage(message);
            } catch (Exception exception) {
                removeConnection(session.getId());

                log.warn(
                        "alert websocket send failed: sessionId={}, safeStack={}",
                        session.getId(),
                        SafeExceptionLogFormatter.format(exception));

                closeQuietly(session);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode payload = objectMapper.readTree(message.getPayload());
            if ("PONG".equals(payload.path("type").asText())
                    && sessions.containsKey(session.getId())) {
                lastPongTimes.put(session.getId(), System.currentTimeMillis());
            }
        } catch (Exception exception) {
            log.debug("ignored invalid alert websocket client message: sessionId={}", session.getId());
        }
    }

    public void sendHeartbeat() {
        long now = System.currentTimeMillis();
        TextMessage ping = new TextMessage("{\"type\":\"PING\",\"time\":" + now + "}");

        for (TenantConnection connection : sessions.values()) {
            WebSocketSession session = connection.session;
            Long lastPong = lastPongTimes.get(session.getId());
            if (!session.isOpen() || lastPong == null || now - lastPong > HEARTBEAT_TIMEOUT_MILLIS) {
                removeAndClose(session, HEARTBEAT_TIMEOUT_STATUS);
                continue;
            }

            try {
                session.sendMessage(ping);
            } catch (Exception exception) {
                log.warn(
                        "alert websocket heartbeat failed: sessionId={}, safeStack={}",
                        session.getId(),
                        SafeExceptionLogFormatter.format(exception));
                removeAndClose(session, CloseStatus.SERVER_ERROR);
            }
        }
    }

    private void removeAndClose(WebSocketSession session, CloseStatus status) {
        removeConnection(session.getId());
        if (session.isOpen()) {
            try {
                session.close(status);
            } catch (IOException ignored) {
            }
        }
    }

    private TenantBinding readBinding(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        if (attributes == null) {
            return null;
        }
        Object tenantId = attributes.get(ATTRIBUTE_TENANT_ID);
        Object userId = attributes.get(ATTRIBUTE_USER_ID);
        Object contextType = attributes.get(ATTRIBUTE_CONTEXT_TYPE);
        if (!(tenantId instanceof Long)
                || (Long) tenantId <= 0
                || !(userId instanceof Long)
                || (Long) userId <= 0
                || !(contextType instanceof TrustedContextType)) {
            return null;
        }
        TrustedContextType type = (TrustedContextType) contextType;
        if (type != TrustedContextType.TENANT
                && type != TrustedContextType.PLATFORM_TENANT) {
            return null;
        }
        return new TenantBinding(
                (Long) tenantId,
                (Long) userId,
                type);
    }

    private void removeConnection(String sessionId) {
        sessions.remove(sessionId);
        lastPongTimes.remove(sessionId);
    }

    private void closeQuietly(WebSocketSession session) {

        if (!session.isOpen()) {
            return;
        }

        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {

        }
    }

    private static final class TenantBinding {
        private final Long tenantId;
        private final Long userId;
        private final TrustedContextType contextType;

        private TenantBinding(
                Long tenantId,
                Long userId,
                TrustedContextType contextType) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.contextType = contextType;
        }
    }

    private static final class TenantConnection {
        private final Long tenantId;
        private final Long userId;
        private final TrustedContextType contextType;
        private final WebSocketSession session;

        private TenantConnection(
                Long tenantId,
                Long userId,
                TrustedContextType contextType,
                WebSocketSession session) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.contextType = contextType;
            this.session = session;
        }
    }
}
