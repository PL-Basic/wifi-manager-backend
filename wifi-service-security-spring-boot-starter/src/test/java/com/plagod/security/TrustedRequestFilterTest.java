package com.plagod.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustedRequestFilterTest {

    private static final String GATEWAY_TOKEN = "a-valid-gateway-token-value";
    private static final String INTERNAL_TOKEN = "a-valid-internal-token-value";

    @Test
    void gatewayRequestIsMarkedTrusted() throws Exception {
        TrustedRequestFilter filter = new TrustedRequestFilter(properties());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/devices");
        request.addHeader(TrustedHeaderNames.GATEWAY_TOKEN, GATEWAY_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, chain(invoked));

        assertTrue(invoked.get());
        assertEquals(TrustedHeaderNames.SOURCE_GATEWAY,
                request.getAttribute(TrustedHeaderNames.TRUSTED_SOURCE_ATTRIBUTE));
    }

    @Test
    void internalRequestIsMarkedTrusted() throws Exception {
        TrustedRequestFilter filter = new TrustedRequestFilter(properties());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/devices");
        request.addHeader(TrustedHeaderNames.INTERNAL_TOKEN, INTERNAL_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, chain(invoked));

        assertTrue(invoked.get());
        assertEquals(TrustedHeaderNames.SOURCE_INTERNAL,
                request.getAttribute(TrustedHeaderNames.TRUSTED_SOURCE_ATTRIBUTE));
    }

    @Test
    void gatewayTokenCannotEnterInternalPath() throws Exception {
        TrustedRequestFilter filter = new TrustedRequestFilter(properties());
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/internal/tenants/context/validate");
        request.addHeader(TrustedHeaderNames.GATEWAY_TOKEN, GATEWAY_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, chain(invoked));

        assertFalse(invoked.get());
        assertEquals(401, response.getStatus());
    }

    private TrustedRequestProperties properties() {
        TrustedRequestProperties properties = new TrustedRequestProperties();
        properties.setGatewayToken(GATEWAY_TOKEN);
        properties.setInternalToken(INTERNAL_TOKEN);
        properties.setInternalTokenRequired(true);
        return properties;
    }

    private FilterChain chain(AtomicBoolean invoked) {
        return (request, response) -> invoked.set(true);
    }
}
