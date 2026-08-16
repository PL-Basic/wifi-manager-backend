package com.plagod.security;

import com.plagod.exception.ApiStatusException;
import com.plagod.request.RequestId;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantRequestContextProviderTest {

    private final TenantRequestContextProvider provider =
            new TenantRequestContextProvider(
                    new TrustedRequestContextResolver());

    @Test
    void resolvesGatewayTenantContextThroughSharedResolver() {
        MockHttpServletRequest request = tenantRequest();

        TrustedRequestContext context =
                provider.requireUserContext(request);

        assertEquals(TrustedContextType.TENANT, context.getContextType());
        assertEquals("11", context.getTenantId());
        assertEquals(Long.valueOf(7L), context.getUserId());
    }

    @Test
    void internalTokenWithoutUserContextIsNotUnlimitedAuthority() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedRequestHeaders.SOURCE_INTERNAL);
        request.addHeader(RequestId.HEADER_NAME, "request-1234567890");

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> provider.requireUserContext(request));

        assertEquals(403, exception.getHttpStatus());
    }

    @Test
    void internalFeignUserContextRemainsTrustedPropagation() {
        MockHttpServletRequest request = tenantRequest();
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedRequestHeaders.SOURCE_INTERNAL);

        TrustedRequestContext context =
                provider.requireUserContext(request);

        assertEquals(
                TrustedSource.INTERNAL_SERVICE,
                context.getTrustedSource());
        assertEquals("11", context.getTenantId());
    }

    @Test
    void tenantHeadersWithoutTrustedSourceAreRejected() {
        MockHttpServletRequest request = tenantRequest();
        request.removeAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE);

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> provider.requireUserContext(request));

        assertEquals(401, exception.getHttpStatus());
    }

    private MockHttpServletRequest tenantRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedRequestHeaders.SOURCE_GATEWAY);
        request.addHeader(TrustedRequestHeaders.USER_ID, "7");
        request.addHeader(TrustedRequestHeaders.USER_ROLE, "1");
        request.addHeader(
                TrustedRequestHeaders.SESSION_ID,
                "session-tenant");
        request.addHeader(
                TrustedRequestHeaders.TOKEN_ID,
                "token-tenant");
        request.addHeader(
                TrustedRequestHeaders.CONTEXT_TYPE,
                "TENANT");
        request.addHeader(TrustedRequestHeaders.TENANT_ID, "11");
        request.addHeader(
                TrustedRequestHeaders.TENANT_CODE,
                "tenant-a");
        request.addHeader(
                TrustedRequestHeaders.TENANT_ROLE,
                "TENANT_ADMIN");
        request.addHeader(
                TrustedRequestHeaders.TENANT_CONTEXT_VERSION,
                "1");
        request.addHeader(
                TrustedRequestHeaders.MEMBER_CONTEXT_VERSION,
                "1");
        request.addHeader(RequestId.HEADER_NAME, "request-1234567890");
        return request;
    }
}
