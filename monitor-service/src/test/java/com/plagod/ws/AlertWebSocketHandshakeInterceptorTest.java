package com.plagod.ws;

import com.plagod.configuration.AlertWebSocketProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AlertWebSocketHandshakeInterceptorTest {

    private static final String GATEWAY_TOKEN = "test-gateway-token";

    private AlertWebSocketHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        AlertWebSocketProperties properties = new AlertWebSocketProperties();
        properties.setAllowedOrigins(Collections.singletonList("http://localhost:5173"));

        interceptor = new AlertWebSocketHandshakeInterceptor();
        ReflectionTestUtils.setField(interceptor, "expectedGatewayToken", GATEWAY_TOKEN);
        ReflectionTestUtils.setField(interceptor, "webSocketProperties", properties);
        interceptor.init();
    }

    @Test
    void acceptsGatewayValidatedAdminIdentityWithoutRawJwt() {
        MockHttpServletRequest servletRequest = trustedRequest(0);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                mock(WebSocketHandler.class),
                attributes);

        assertTrue(accepted);
        assertEquals(7L, attributes.get("userId"));
        assertEquals("admin", attributes.get("username"));
        assertEquals(0, attributes.get("role"));
    }

    @Test
    void rejectsOrdinaryUserEvenWhenRequestComesFromGateway() {
        MockHttpServletRequest servletRequest = trustedRequest(2);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                mock(WebSocketHandler.class),
                new HashMap<>());

        assertFalse(accepted);
        assertEquals(HttpStatus.FORBIDDEN.value(), servletResponse.getStatus());
    }

    @Test
    void rejectsRequestWithoutGatewayCredential() {
        MockHttpServletRequest servletRequest = trustedRequest(0);
        servletRequest.removeHeader("X-Gateway-Token");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                mock(WebSocketHandler.class),
                new HashMap<>());

        assertFalse(accepted);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResponse.getStatus());
    }

    private MockHttpServletRequest trustedRequest(int role) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/alerts");
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("Sec-WebSocket-Protocol", "access_token");
        request.addHeader("X-Gateway-Token", GATEWAY_TOKEN);
        request.addHeader("X-User-Id", "7");
        request.addHeader("X-User-Name", "admin");
        request.addHeader("X-User-Role", String.valueOf(role));
        return request;
    }
}
