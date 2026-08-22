package com.plagod.ws;

import com.plagod.configuration.AlertWebSocketProperties;
import com.plagod.security.TrustedContextType;
import com.plagod.security.TrustedRequestContextResolver;
import com.plagod.security.TrustedRequestHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class AlertWebSocketHandshakeInterceptorTest {

    private static final String GATEWAY_TOKEN = "test-gateway-token";
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    private AlertWebSocketHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        AlertWebSocketProperties properties = new AlertWebSocketProperties();
        properties.setAllowedOrigins(
                Collections.singletonList(ALLOWED_ORIGIN));

        interceptor = new AlertWebSocketHandshakeInterceptor();
        ReflectionTestUtils.setField(
                interceptor,
                "expectedGatewayToken",
                GATEWAY_TOKEN);
        ReflectionTestUtils.setField(
                interceptor,
                "webSocketProperties",
                properties);
        ReflectionTestUtils.setField(
                interceptor,
                "contextResolver",
                new TrustedRequestContextResolver());
        interceptor.init();
    }

    @Test
    void acceptsTrustedTenantAdministratorAndBindsOnlyTenantIdentity() {
        MockHttpServletRequest servletRequest =
                tenantRequest("TENANT_ADMIN");
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = handshake(servletRequest, attributes);

        assertTrue(accepted);
        assertEquals(3, attributes.size());
        assertEquals(
                11L,
                attributes.get(AlertWebSocketHandler.ATTRIBUTE_TENANT_ID));
        assertEquals(
                7L,
                attributes.get(AlertWebSocketHandler.ATTRIBUTE_USER_ID));
        assertEquals(
                TrustedContextType.TENANT,
                attributes.get(
                        AlertWebSocketHandler.ATTRIBUTE_CONTEXT_TYPE));
    }

    @Test
    void acceptsLegalPlatformTenantWithoutCheckingActionAuthorityName() {
        MockHttpServletRequest servletRequest = platformTenantRequest();
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = handshake(servletRequest, attributes);

        assertTrue(accepted);
        assertEquals(
                11L,
                attributes.get(AlertWebSocketHandler.ATTRIBUTE_TENANT_ID));
        assertEquals(
                TrustedContextType.PLATFORM_TENANT,
                attributes.get(
                        AlertWebSocketHandler.ATTRIBUTE_CONTEXT_TYPE));
    }

    @Test
    void rejectsPlatformContextWithoutTenantSelection() {
        MockHttpServletRequest servletRequest = platformRequest();
        MockHttpServletResponse servletResponse =
                new MockHttpServletResponse();

        boolean accepted = handshake(
                servletRequest,
                servletResponse,
                new HashMap<>());

        assertFalse(accepted);
        assertEquals(
                HttpStatus.FORBIDDEN.value(),
                servletResponse.getStatus());
    }

    @Test
    void rejectsOrdinaryTenantMember() {
        MockHttpServletRequest servletRequest =
                tenantRequest("TENANT_MEMBER");
        MockHttpServletResponse servletResponse =
                new MockHttpServletResponse();

        boolean accepted = handshake(
                servletRequest,
                servletResponse,
                new HashMap<>());

        assertFalse(accepted);
        assertEquals(
                HttpStatus.FORBIDDEN.value(),
                servletResponse.getStatus());
    }

    @Test
    void rejectsInternalSourceEvenWithLegalTenantAdministratorContext() {
        MockHttpServletRequest servletRequest =
                tenantRequest("TENANT_ADMIN");
        servletRequest.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedRequestHeaders.SOURCE_INTERNAL);
        MockHttpServletResponse servletResponse =
                new MockHttpServletResponse();

        boolean accepted = handshake(
                servletRequest,
                servletResponse,
                new HashMap<>());

        assertFalse(accepted);
        assertEquals(
                HttpStatus.FORBIDDEN.value(),
                servletResponse.getStatus());
    }

    @Test
    void rejectsForgedOrMissingTrustedSource() {
        MockHttpServletRequest servletRequest =
                tenantRequest("TENANT_ADMIN");
        servletRequest.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                "BROWSER");
        MockHttpServletResponse servletResponse =
                new MockHttpServletResponse();

        boolean accepted = handshake(
                servletRequest,
                servletResponse,
                new HashMap<>());

        assertFalse(accepted);
        assertEquals(
                HttpStatus.UNAUTHORIZED.value(),
                servletResponse.getStatus());

        MockHttpServletRequest missingSource =
                tenantRequest("TENANT_ADMIN");
        missingSource.removeAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE);
        MockHttpServletResponse missingSourceResponse =
                new MockHttpServletResponse();

        assertFalse(handshake(
                missingSource,
                missingSourceResponse,
                new HashMap<>()));
        assertEquals(
                HttpStatus.UNAUTHORIZED.value(),
                missingSourceResponse.getStatus());
    }

    @Test
    void mapsResolverPermissionFailureToForbidden() {
        MockHttpServletRequest servletRequest =
                platformTenantRequest();
        servletRequest.removeHeader(TrustedRequestHeaders.USER_ROLE);
        servletRequest.addHeader(TrustedRequestHeaders.USER_ROLE, "1");
        MockHttpServletResponse servletResponse =
                new MockHttpServletResponse();

        boolean accepted = handshake(
                servletRequest,
                servletResponse,
                new HashMap<>());

        assertFalse(accepted);
        assertEquals(
                HttpStatus.FORBIDDEN.value(),
                servletResponse.getStatus());
    }

    @Test
    void rejectsMissingTrustedContext() {
        MockHttpServletRequest servletRequest = baseRequest();
        servletRequest.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedRequestHeaders.SOURCE_GATEWAY);
        MockHttpServletResponse servletResponse =
                new MockHttpServletResponse();

        boolean accepted = handshake(
                servletRequest,
                servletResponse,
                new HashMap<>());

        assertFalse(accepted);
        assertEquals(
                HttpStatus.UNAUTHORIZED.value(),
                servletResponse.getStatus());
    }

    @Test
    void rejectsNonServletServerRequest() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Origin", ALLOWED_ORIGIN);
        headers.add("Sec-WebSocket-Protocol", "access_token");
        headers.add(TrustedRequestHeaders.GATEWAY_TOKEN, GATEWAY_TOKEN);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create("/ws/alerts"));
        MockHttpServletResponse servletResponse =
                new MockHttpServletResponse();

        boolean accepted = interceptor.beforeHandshake(
                request,
                new ServletServerHttpResponse(servletResponse),
                mock(WebSocketHandler.class),
                new HashMap<>());

        assertFalse(accepted);
        assertEquals(
                HttpStatus.UNAUTHORIZED.value(),
                servletResponse.getStatus());
    }

    @Test
    void rejectsMissingGatewayCredential() {
        MockHttpServletRequest servletRequest =
                tenantRequest("TENANT_ADMIN");
        servletRequest.removeHeader(
                TrustedRequestHeaders.GATEWAY_TOKEN);
        MockHttpServletResponse servletResponse =
                new MockHttpServletResponse();

        boolean accepted = handshake(
                servletRequest,
                servletResponse,
                new HashMap<>());

        assertFalse(accepted);
        assertEquals(
                HttpStatus.UNAUTHORIZED.value(),
                servletResponse.getStatus());
    }

    @Test
    void rejectsMissingAccessTokenSubprotocol() {
        MockHttpServletRequest servletRequest =
                tenantRequest("TENANT_ADMIN");
        servletRequest.removeHeader("Sec-WebSocket-Protocol");
        MockHttpServletResponse servletResponse =
                new MockHttpServletResponse();

        boolean accepted = handshake(
                servletRequest,
                servletResponse,
                new HashMap<>());

        assertFalse(accepted);
        assertEquals(
                HttpStatus.UNAUTHORIZED.value(),
                servletResponse.getStatus());
    }

    @Test
    void rejectsMissingTenantIdentity() {
        MockHttpServletRequest servletRequest =
                tenantRequest("TENANT_ADMIN");
        servletRequest.removeHeader(TrustedRequestHeaders.TENANT_ID);
        MockHttpServletResponse servletResponse =
                new MockHttpServletResponse();

        boolean accepted = handshake(
                servletRequest,
                servletResponse,
                new HashMap<>());

        assertFalse(accepted);
        assertEquals(
                HttpStatus.UNAUTHORIZED.value(),
                servletResponse.getStatus());
    }

    @Test
    void redactsUntrustedOriginFromRejectionLog(
            CapturedOutput output) {
        String canaryOrigin = "http://origin-canary-secret.invalid";
        String canaryPath = "/ws/alerts/path-canary-secret";
        MockHttpServletRequest servletRequest =
                tenantRequest("TENANT_ADMIN");
        servletRequest.setRequestURI(canaryPath);
        servletRequest.removeHeader("Origin");
        servletRequest.addHeader("Origin", canaryOrigin);
        MockHttpServletResponse servletResponse =
                new MockHttpServletResponse();

        boolean accepted = handshake(
                servletRequest,
                servletResponse,
                new HashMap<>());

        assertFalse(accepted);
        assertEquals(
                HttpStatus.FORBIDDEN.value(),
                servletResponse.getStatus());
        assertTrue(output.getAll().contains("[REDACTED]"));
        assertFalse(output.getAll().contains(canaryOrigin));
        assertFalse(output.getAll().contains(canaryPath));
    }

    private boolean handshake(
            MockHttpServletRequest servletRequest,
            Map<String, Object> attributes) {
        return handshake(
                servletRequest,
                new MockHttpServletResponse(),
                attributes);
    }

    private boolean handshake(
            MockHttpServletRequest servletRequest,
            MockHttpServletResponse servletResponse,
            Map<String, Object> attributes) {
        return interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                mock(WebSocketHandler.class),
                attributes);
    }

    private MockHttpServletRequest tenantRequest(String tenantRole) {
        MockHttpServletRequest request = baseTrustedRequest();
        addUserIdentity(request, 1, TrustedContextType.TENANT);
        addTenantIdentity(request);
        request.addHeader(
                TrustedRequestHeaders.TENANT_ROLE,
                tenantRole);
        request.addHeader(
                TrustedRequestHeaders.MEMBER_CONTEXT_VERSION,
                "1");
        return request;
    }

    private MockHttpServletRequest platformTenantRequest() {
        MockHttpServletRequest request = baseTrustedRequest();
        addUserIdentity(
                request,
                0,
                TrustedContextType.PLATFORM_TENANT);
        addTenantIdentity(request);
        request.addHeader(
                TrustedRequestHeaders.PLATFORM_AUTHORITIES,
                "TENANT_MANAGE");
        return request;
    }

    private MockHttpServletRequest platformRequest() {
        MockHttpServletRequest request = baseTrustedRequest();
        addUserIdentity(request, 0, TrustedContextType.PLATFORM);
        request.addHeader(
                TrustedRequestHeaders.PLATFORM_AUTHORITIES,
                "TENANT_MANAGE");
        return request;
    }

    private MockHttpServletRequest baseTrustedRequest() {
        MockHttpServletRequest request = baseRequest();
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedRequestHeaders.SOURCE_GATEWAY);
        return request;
    }

    private MockHttpServletRequest baseRequest() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/ws/alerts");
        request.addHeader("Origin", ALLOWED_ORIGIN);
        request.addHeader(
                "Sec-WebSocket-Protocol",
                "access_token");
        request.addHeader(
                TrustedRequestHeaders.GATEWAY_TOKEN,
                GATEWAY_TOKEN);
        return request;
    }

    private void addUserIdentity(
            MockHttpServletRequest request,
            int globalRole,
            TrustedContextType contextType) {
        request.addHeader(TrustedRequestHeaders.USER_ID, "7");
        request.addHeader(
                TrustedRequestHeaders.USER_ROLE,
                String.valueOf(globalRole));
        request.addHeader(
                TrustedRequestHeaders.SESSION_ID,
                "session-websocket");
        request.addHeader(
                TrustedRequestHeaders.TOKEN_ID,
                "token-websocket");
        request.addHeader(
                TrustedRequestHeaders.CONTEXT_TYPE,
                contextType.name());
    }

    private void addTenantIdentity(MockHttpServletRequest request) {
        request.addHeader(TrustedRequestHeaders.TENANT_ID, "11");
        request.addHeader(
                TrustedRequestHeaders.TENANT_CODE,
                "tenant-a");
        request.addHeader(
                TrustedRequestHeaders.TENANT_CONTEXT_VERSION,
                "1");
    }
}
