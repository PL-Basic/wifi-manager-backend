package com.plagod.security;

import com.plagod.request.RequestId;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("deprecation")
class TrustedFeignRequestInterceptorTest {

    private static final String INTERNAL_TOKEN = "a-valid-internal-token-value";

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void rejectsMissingOrShortInternalToken() {
        assertThrows(
                IllegalStateException.class,
                () -> new TrustedFeignRequestInterceptor("short-token"));
        assertThrows(
                IllegalStateException.class,
                () -> new TrustedFeignRequestInterceptor(" "));
    }

    @Test
    void usesFixedTenantContextHeaderNames() {
        assertEquals("X-Session-Id", TrustedHeaderNames.SESSION_ID);
        assertEquals("X-Token-Id", TrustedHeaderNames.TOKEN_ID);
        assertEquals("X-Context-Type", TrustedHeaderNames.CONTEXT_TYPE);
        assertEquals("X-Tenant-Id", TrustedHeaderNames.TENANT_ID);
        assertEquals("X-Tenant-Code", TrustedHeaderNames.TENANT_CODE);
        assertEquals("X-Tenant-Role", TrustedHeaderNames.TENANT_ROLE);
        assertEquals("X-Tenant-Context-Version", TrustedHeaderNames.TENANT_CONTEXT_VERSION);
        assertEquals("X-Member-Context-Version", TrustedHeaderNames.MEMBER_CONTEXT_VERSION);
        assertEquals("X-Platform-Authorities", TrustedHeaderNames.PLATFORM_AUTHORITIES);
        assertEquals(
                Arrays.asList(
                        TrustedHeaderNames.USER_ID,
                        TrustedHeaderNames.USER_NAME,
                        TrustedHeaderNames.USER_ROLE,
                        TrustedHeaderNames.SESSION_ID,
                        TrustedHeaderNames.TOKEN_ID,
                        TrustedHeaderNames.CONTEXT_TYPE,
                        TrustedHeaderNames.TENANT_ID,
                        TrustedHeaderNames.TENANT_CODE,
                        TrustedHeaderNames.TENANT_ROLE,
                        TrustedHeaderNames.TENANT_CONTEXT_VERSION,
                        TrustedHeaderNames.MEMBER_CONTEXT_VERSION,
                        TrustedHeaderNames.PLATFORM_AUTHORITIES,
                        RequestId.HEADER_NAME),
                TrustedHeaderNames.PROPAGATED_CONTEXT_HEADERS);
    }

    @Test
    void propagatesOnlyTrustedContextAndInternalToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/devices");
        request.setAttribute(
                TrustedHeaderNames.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedHeaderNames.SOURCE_GATEWAY);
        request.setAttribute(
                RequestId.REQUEST_ATTRIBUTE,
                "request-id-00000001");
        request.addHeader(TrustedHeaderNames.USER_ID, "17");
        request.addHeader(TrustedHeaderNames.USER_NAME, "p1accept0804");
        request.addHeader(TrustedHeaderNames.USER_ROLE, "1");
        request.addHeader(TrustedHeaderNames.SESSION_ID, "session-1");
        request.addHeader(TrustedHeaderNames.TOKEN_ID, "token-1");
        request.addHeader(TrustedHeaderNames.CONTEXT_TYPE, "TENANT");
        request.addHeader(TrustedHeaderNames.TENANT_ID, "3");
        request.addHeader(TrustedHeaderNames.TENANT_CODE, "default-tenant");
        request.addHeader(TrustedHeaderNames.TENANT_ROLE, "TENANT_ADMIN");
        request.addHeader(TrustedHeaderNames.TENANT_CONTEXT_VERSION, "4");
        request.addHeader(TrustedHeaderNames.MEMBER_CONTEXT_VERSION, "5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = templateWithUntrustedHeaders();
        new TrustedFeignRequestInterceptor(INTERNAL_TOKEN).apply(template);

        assertHeader(template, TrustedHeaderNames.INTERNAL_TOKEN, INTERNAL_TOKEN);
        assertHeader(template, TrustedHeaderNames.USER_ID, "17");
        assertHeader(template, TrustedHeaderNames.SESSION_ID, "session-1");
        assertHeader(template, TrustedHeaderNames.TOKEN_ID, "token-1");
        assertHeader(template, TrustedHeaderNames.CONTEXT_TYPE, "TENANT");
        assertHeader(template, TrustedHeaderNames.TENANT_ID, "3");
        assertHeader(template, TrustedHeaderNames.TENANT_CONTEXT_VERSION, "4");
        assertHeader(template, TrustedHeaderNames.MEMBER_CONTEXT_VERSION, "5");
        assertHeader(template, RequestId.HEADER_NAME, "request-id-00000001");
        assertFalse(template.headers().containsKey(
                TrustedHeaderNames.PLATFORM_AUTHORITIES));
        assertFalse(template.headers().containsKey(TrustedHeaderNames.GATEWAY_TOKEN));
        assertFalse(template.headers().containsKey(TrustedHeaderNames.AUTHORIZATION));
        assertFalse(template.headers().containsKey(TrustedHeaderNames.COOKIE));
    }

    @Test
    void removesCallerSuppliedContextWithoutTrustedServletRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/devices");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = templateWithUntrustedHeaders();
        new TrustedFeignRequestInterceptor(INTERNAL_TOKEN).apply(template);

        assertHeader(template, TrustedHeaderNames.INTERNAL_TOKEN, INTERNAL_TOKEN);
        assertFalse(template.headers().containsKey(TrustedHeaderNames.USER_ID));
        assertFalse(template.headers().containsKey(TrustedHeaderNames.TENANT_ID));
        assertFalse(template.headers().containsKey(RequestId.HEADER_NAME));
        assertFalse(template.headers().containsKey(TrustedHeaderNames.GATEWAY_TOKEN));
        assertFalse(template.headers().containsKey(TrustedHeaderNames.AUTHORIZATION));
        assertFalse(template.headers().containsKey(TrustedHeaderNames.COOKIE));
    }

    @Test
    void internalServicePropagatesRequestIdWithoutUserAuthority() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/internal/jobs/run");
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedRequestHeaders.SOURCE_INTERNAL);
        request.setAttribute(
                RequestId.REQUEST_ATTRIBUTE,
                "request-id-00000001");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));

        RequestTemplate template = templateWithUntrustedHeaders();
        new TrustedFeignRequestInterceptor(INTERNAL_TOKEN).apply(template);

        assertHeader(
                template,
                TrustedRequestHeaders.INTERNAL_TOKEN,
                INTERNAL_TOKEN);
        assertHeader(
                template,
                RequestId.HEADER_NAME,
                "request-id-00000001");
        assertFalse(template.headers().containsKey(
                TrustedRequestHeaders.USER_ID));
        assertFalse(template.headers().containsKey(
                TrustedRequestHeaders.TENANT_ID));
        assertFalse(template.headers().containsKey(
                TrustedRequestHeaders.PLATFORM_AUTHORITIES));
    }

    private RequestTemplate templateWithUntrustedHeaders() {
        RequestTemplate template = new RequestTemplate();
        template.header(TrustedHeaderNames.INTERNAL_TOKEN, "caller-token");
        template.header(TrustedHeaderNames.GATEWAY_TOKEN, "caller-gateway-token");
        template.header(TrustedHeaderNames.USER_ID, "999");
        template.header(TrustedHeaderNames.TENANT_ID, "999");
        template.header(RequestId.HEADER_NAME, "forged-request-id");
        template.header(TrustedHeaderNames.AUTHORIZATION, "Bearer browser-token");
        template.header(TrustedHeaderNames.COOKIE, "wifi_refresh=secret");
        return template;
    }

    private void assertHeader(RequestTemplate template, String name, String expected) {
        Collection<String> values = template.headers().get(name);
        assertEquals(1, values == null ? 0 : values.size());
        assertEquals(expected, values.iterator().next());
    }
}
