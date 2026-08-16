package com.plagod.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class AlertWebSocketHandlerSafeLoggingTest {

    private static final String CANARY =
            "websocket-exception-canary-secret";

    @Test
    void transportFailureLogKeepsTypeButDropsExceptionMessage(
            CapturedOutput output) throws Exception {
        AlertWebSocketHandler handler =
                new AlertWebSocketHandler(new ObjectMapper());
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("test-session");
        when(session.isOpen()).thenReturn(false);

        handler.handleTransportError(
                session,
                new IllegalStateException(CANARY));

        assertTrue(output.getAll().contains("IllegalStateException"));
        assertFalse(output.getAll().contains(CANARY));
    }
}
