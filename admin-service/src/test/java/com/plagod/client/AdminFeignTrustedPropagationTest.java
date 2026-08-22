package com.plagod.client;

import com.plagod.request.RequestId;
import com.plagod.security.TrustedFeignRequestInterceptor;
import com.plagod.security.TrustedRequestContextResolver;
import com.plagod.security.TrustedRequestHeaders;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminFeignTrustedPropagationTest {

    private static final String INTERNAL_TOKEN =
            "admin-service-internal-test-token";

    private final TrustedFeignRequestInterceptor interceptor =
            new TrustedFeignRequestInterceptor(
                    INTERNAL_TOKEN,
                    new TrustedRequestContextResolver());

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void tenantContextReplacesManualFeignIdentityAndTenant()
            throws Exception {
        bind(tenantRequest());
        RequestTemplate template = forgedTemplate();

        interceptor.apply(template);

        assertHeader(
                template,
                TrustedRequestHeaders.INTERNAL_TOKEN,
                INTERNAL_TOKEN);
        assertHeader(
                template,
                TrustedRequestHeaders.USER_ID,
                "9");
        assertHeader(
                template,
                TrustedRequestHeaders.TENANT_ID,
                "101");
        assertHeader(
                template,
                RequestId.HEADER_NAME,
                "request-id-00000001");
        assertNull(header(
                template,
                TrustedRequestHeaders.AUTHORIZATION));
        assertNull(header(
                template,
                TrustedRequestHeaders.COOKIE));
        assertNull(header(
                template,
                TrustedRequestHeaders.GATEWAY_TOKEN));
    }

    @Test
    void platformTenantNeverPropagatesMemberIdentity()
            throws Exception {
        bind(platformTenantRequest());
        RequestTemplate template = forgedTemplate();

        interceptor.apply(template);

        assertHeader(
                template,
                TrustedRequestHeaders.CONTEXT_TYPE,
                "PLATFORM_TENANT");
        assertHeader(
                template,
                TrustedRequestHeaders.TENANT_ID,
                "101");
        assertHeader(
                template,
                TrustedRequestHeaders.PLATFORM_AUTHORITIES,
                "TENANT_READ");
        assertNull(header(
                template,
                TrustedRequestHeaders.TENANT_ROLE));
        assertNull(header(
                template,
                TrustedRequestHeaders.MEMBER_CONTEXT_VERSION));
    }

    @Test
    void pureInternalCallCarriesOnlyServiceTokenAndRequestId()
            throws Exception {
        MockHttpServletRequest request = baseRequest(
                TrustedRequestHeaders.SOURCE_INTERNAL);
        bind(request);
        RequestTemplate template = forgedTemplate();

        interceptor.apply(template);

        assertHeader(
                template,
                TrustedRequestHeaders.INTERNAL_TOKEN,
                INTERNAL_TOKEN);
        assertHeader(
                template,
                RequestId.HEADER_NAME,
                "request-id-00000001");
        for (String header :
                TrustedRequestHeaders.IDENTITY_CONTEXT_HEADERS) {
            assertNull(header(template, header));
        }
    }

    @Test
    void adminFeignClientsDeclareNoManualTrustedHeaders() {
        Class<?>[] clients = {
                DeviceServiceClient.class,
                MonitorServiceClient.class,
                TenantServiceClient.class,
                UserServiceClient.class
        };

        for (Class<?> client : clients) {
            for (Method method : client.getDeclaredMethods()) {
                for (Annotation[] parameterAnnotations
                        : method.getParameterAnnotations()) {
                    assertFalse(Arrays.stream(parameterAnnotations)
                            .anyMatch(annotation ->
                                    isTrustedRequestHeader(
                                            annotation)));
                }
            }
        }
    }

    private boolean isTrustedRequestHeader(
            Annotation annotation) {
        if (annotation.annotationType() != RequestHeader.class) {
            return false;
        }
        String name = ((RequestHeader) annotation).value();
        return TrustedRequestHeaders.PROPAGATED_CONTEXT_HEADERS
                .contains(name)
                || TrustedRequestHeaders.GATEWAY_TOKEN.equals(name)
                || TrustedRequestHeaders.INTERNAL_TOKEN.equals(name);
    }

    private RequestTemplate forgedTemplate() {
        RequestTemplate template = new RequestTemplate();
        template.header(
                TrustedRequestHeaders.AUTHORIZATION,
                "Bearer browser-token");
        template.header(
                TrustedRequestHeaders.COOKIE,
                "SESSION=browser-cookie");
        template.header(
                TrustedRequestHeaders.GATEWAY_TOKEN,
                "forged-gateway-token");
        template.header(
                TrustedRequestHeaders.INTERNAL_TOKEN,
                "forged-internal-token");
        template.header(
                TrustedRequestHeaders.USER_ID,
                "999");
        template.header(
                TrustedRequestHeaders.TENANT_ID,
                "202");
        template.header(
                TrustedRequestHeaders.TENANT_ROLE,
                "TENANT_OWNER");
        template.header(
                TrustedRequestHeaders.MEMBER_CONTEXT_VERSION,
                "99");
        template.header(
                RequestId.HEADER_NAME,
                "forged-request-id-01");
        return template;
    }

    private MockHttpServletRequest tenantRequest() {
        MockHttpServletRequest request =
                userRequest(1, "TENANT");
        request.addHeader(TrustedRequestHeaders.TENANT_ID, "101");
        request.addHeader(
                TrustedRequestHeaders.TENANT_CODE,
                "tenant-a");
        request.addHeader(
                TrustedRequestHeaders.TENANT_ROLE,
                "TENANT_ADMIN");
        request.addHeader(
                TrustedRequestHeaders.TENANT_CONTEXT_VERSION,
                "7");
        request.addHeader(
                TrustedRequestHeaders.MEMBER_CONTEXT_VERSION,
                "11");
        return request;
    }

    private MockHttpServletRequest platformTenantRequest() {
        MockHttpServletRequest request =
                userRequest(0, "PLATFORM_TENANT");
        request.addHeader(TrustedRequestHeaders.TENANT_ID, "101");
        request.addHeader(
                TrustedRequestHeaders.TENANT_CODE,
                "tenant-a");
        request.addHeader(
                TrustedRequestHeaders.TENANT_CONTEXT_VERSION,
                "7");
        request.addHeader(
                TrustedRequestHeaders.PLATFORM_AUTHORITIES,
                "TENANT_READ");
        return request;
    }

    private MockHttpServletRequest userRequest(
            int role,
            String contextType) {
        MockHttpServletRequest request = baseRequest(
                TrustedRequestHeaders.SOURCE_GATEWAY);
        request.addHeader(TrustedRequestHeaders.USER_ID, "9");
        request.addHeader(
                TrustedRequestHeaders.USER_NAME,
                "admin-a");
        request.addHeader(
                TrustedRequestHeaders.USER_ROLE,
                String.valueOf(role));
        request.addHeader(
                TrustedRequestHeaders.SESSION_ID,
                "session-00000001");
        request.addHeader(
                TrustedRequestHeaders.TOKEN_ID,
                "token-0000000001");
        request.addHeader(
                TrustedRequestHeaders.CONTEXT_TYPE,
                contextType);
        return request;
    }

    private MockHttpServletRequest baseRequest(String source) {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                source);
        request.addHeader(
                RequestId.HEADER_NAME,
                "request-id-00000001");
        return request;
    }

    private void bind(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));
    }

    private void assertHeader(
            RequestTemplate template,
            String name,
            String expected) {
        assertEquals(expected, header(template, name));
    }

    private String header(
            RequestTemplate template,
            String name) {
        Collection<String> values =
                template.headers().get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        assertEquals(1, values.size());
        return values.iterator().next();
    }
}
