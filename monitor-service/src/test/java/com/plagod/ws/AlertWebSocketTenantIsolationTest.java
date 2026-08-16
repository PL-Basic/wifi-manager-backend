package com.plagod.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.security.TrustedContextType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertWebSocketTenantIsolationTest {

    private AlertWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AlertWebSocketHandler(new ObjectMapper());
    }

    @Test
    void broadcastsOnlyToMatchingImmutableTenantBinding()
            throws Exception {
        Map<String, Object> attributesA = binding(
                11L,
                TrustedContextType.TENANT);
        WebSocketSession tenantA = session("tenant-a", attributesA);
        WebSocketSession tenantB = session(
                "tenant-b",
                binding(22L, TrustedContextType.PLATFORM_TENANT));
        handler.afterConnectionEstablished(tenantA);
        handler.afterConnectionEstablished(tenantB);

        // 握手后的 attributes 即使被外部修改，也不能改变已保存的连接归属。
        attributesA.put(
                AlertWebSocketHandler.ATTRIBUTE_TENANT_ID,
                22L);
        handler.broadcastToTenant(
                11L,
                java.util.Collections.singletonMap("type", "alert"));

        verify(tenantA).sendMessage(argThat(message ->
                message instanceof TextMessage
                        && ((TextMessage) message)
                        .getPayload().contains("\"type\":\"alert\"")));
        verify(tenantB, never()).sendMessage(any());
    }

    @Test
    void rejectsIncompleteBindingDuringSecondValidation()
            throws Exception {
        WebSocketSession session = session(
                "invalid-binding",
                new HashMap<>());

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        assertConnectionStateEmpty();
    }

    @Test
    void disconnectRemovesConnectionAndPongState() throws Exception {
        WebSocketSession session = session(
                "disconnect",
                binding(11L, TrustedContextType.TENANT));
        handler.afterConnectionEstablished(session);

        handler.afterConnectionClosed(
                session,
                CloseStatus.NORMAL);

        assertConnectionStateEmpty();
    }

    @Test
    void transportErrorRemovesConnectionAndPongState()
            throws Exception {
        WebSocketSession session = session(
                "transport-error",
                binding(11L, TrustedContextType.TENANT));
        handler.afterConnectionEstablished(session);

        handler.handleTransportError(
                session,
                new IOException("transport failed"));

        assertConnectionStateEmpty();
    }

    @Test
    void sendFailureRemovesConnectionAndPongState() throws Exception {
        WebSocketSession session = session(
                "send-failure",
                binding(11L, TrustedContextType.TENANT));
        doThrow(new IOException("send failed"))
                .when(session).sendMessage(any());
        handler.afterConnectionEstablished(session);

        handler.broadcastToTenant(
                11L,
                java.util.Collections.singletonMap("type", "alert"));

        assertConnectionStateEmpty();
    }

    @Test
    void heartbeatTimeoutRemovesConnectionAndPongState()
            throws Exception {
        WebSocketSession session = session(
                "heartbeat-timeout",
                binding(11L, TrustedContextType.TENANT));
        handler.afterConnectionEstablished(session);
        pongTimes().put("heartbeat-timeout", 0L);

        handler.sendHeartbeat();

        assertConnectionStateEmpty();
        verify(session).close(any(CloseStatus.class));
    }

    @Test
    void activeConnectionStillReceivesHeartbeat() throws Exception {
        WebSocketSession session = session(
                "heartbeat-active",
                binding(11L, TrustedContextType.TENANT));
        handler.afterConnectionEstablished(session);

        handler.sendHeartbeat();

        verify(session).sendMessage(argThat(message ->
                message instanceof TextMessage
                        && ((TextMessage) message)
                        .getPayload().contains("\"type\":\"PING\"")));
        assertEquals(1, connections().size());
        assertEquals(1, pongTimes().size());
    }

    private WebSocketSession session(
            String id,
            Map<String, Object> attributes) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private Map<String, Object> binding(
            Long tenantId,
            TrustedContextType contextType) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(
                AlertWebSocketHandler.ATTRIBUTE_TENANT_ID,
                tenantId);
        attributes.put(
                AlertWebSocketHandler.ATTRIBUTE_USER_ID,
                7L);
        attributes.put(
                AlertWebSocketHandler.ATTRIBUTE_CONTEXT_TYPE,
                contextType);
        return attributes;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> connections() {
        return (Map<String, Object>) ReflectionTestUtils.getField(
                handler,
                "sessions");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> pongTimes() {
        return (Map<String, Long>) ReflectionTestUtils.getField(
                handler,
                "lastPongTimes");
    }

    private void assertConnectionStateEmpty() {
        assertTrue(connections().isEmpty());
        assertTrue(pongTimes().isEmpty());
    }
}
