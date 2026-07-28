package com.plagod.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final String ACCESS_TOKEN_PROTOCOL = "access_token";

    private static final int SEND_TIME_LIMIT_MILLIS = 10_000;

    private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

    /**
     * 按连接 ID 保存并发安全的 Session 包装器。
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

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

        Integer role = readRole(session);

        // Handler 层再次防止非管理员进入广播集合。
        if (!isAdmin(role)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        WebSocketSession concurrentSession = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MILLIS, SEND_BUFFER_LIMIT_BYTES);

        sessions.put(session.getId(), concurrentSession);

        log.info("alert websocket connected: sessionId={}, userId={}, total={}", session.getId(), session.getAttributes().get("userId"), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

        sessions.remove(session.getId());

        log.info("alert websocket closed: sessionId={}, status={}, total={}", session.getId(), status, sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {

        sessions.remove(session.getId());

        log.warn("alert websocket transport error: sessionId={}, message={}", session.getId(), exception.getMessage());

        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    public void broadcast(Object payload) {
        if (sessions.isEmpty()) {
            return;
        }

        String json;

        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            log.warn("alert websocket serialization failed: {}", exception.getMessage());
            return;
        }

        TextMessage message = new TextMessage(json);

        for (WebSocketSession session : sessions.values()) {
            if (!session.isOpen()) {
                sessions.remove(session.getId());
                continue;
            }

            try {
                // ConcurrentWebSocketSessionDecorator
                // 会串行化同一连接上的并发发送。
                session.sendMessage(message);
            } catch (Exception exception) {
                sessions.remove(session.getId());

                log.warn("alert websocket send failed: sessionId={}, message={}", session.getId(), exception.getMessage());

                closeQuietly(session);
            }
        }
    }

    private Integer readRole(WebSocketSession session) {
        Object value = session.getAttributes().get("role");

        if (value == null) {
            return null;
        }

        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isAdmin(Integer role) {
        return Integer.valueOf(0).equals(role) || Integer.valueOf(1).equals(role);
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
}