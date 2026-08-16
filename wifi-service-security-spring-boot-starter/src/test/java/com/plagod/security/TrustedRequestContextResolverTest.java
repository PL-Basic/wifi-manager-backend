package com.plagod.security;

import com.plagod.request.RequestId;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrustedRequestContextResolverTest {

    private static final String REQUEST_ID = "request-id-00000001";
    private final TrustedRequestContextResolver resolver =
            new TrustedRequestContextResolver();

    @Test
    void anonymousGatewayRequestDoesNotInventUserContext() {
        MockHttpServletRequest request =
                trustedRequest(TrustedRequestHeaders.SOURCE_GATEWAY);

        assertNull(resolver.resolve(request));
        assertNull(TrustedRequestContextResolver.resolved(request));
    }

    @Test
    void internalTokenWithoutUserHeadersBuildsOnlyServiceIdentity() {
        MockHttpServletRequest request =
                trustedRequest(TrustedRequestHeaders.SOURCE_INTERNAL);

        TrustedRequestContext context = resolver.resolve(request);

        assertEquals(
                TrustedSource.INTERNAL_SERVICE,
                context.getTrustedSource());
        assertNull(context.getUserId());
        assertNull(context.getContextType());
        assertSame(
                context,
                TrustedRequestContextResolver.resolved(request));
    }

    @Test
    void resolvesThreeUserContextsWithoutMixingMemberIdentity() {
        TrustedRequestContext platform =
                resolver.resolve(platformRequest());
        TrustedRequestContext tenant =
                resolver.resolve(tenantRequest());
        MockHttpServletRequest managedRequest = baseUserRequest(
                TrustedRequestHeaders.SOURCE_INTERNAL,
                "0",
                "PLATFORM_TENANT");
        addTenant(managedRequest, false);
        managedRequest.addHeader(
                TrustedRequestHeaders.PLATFORM_AUTHORITIES,
                "TENANT_MANAGE,AUDIT_READ");
        TrustedRequestContext managed =
                resolver.resolve(managedRequest);

        assertEquals(TrustedContextType.PLATFORM, platform.getContextType());
        assertNull(platform.getTenantId());
        assertEquals(TrustedContextType.TENANT, tenant.getContextType());
        assertEquals("TENANT_ADMIN", tenant.getTenantRole());
        assertEquals(
                TrustedContextType.PLATFORM_TENANT,
                managed.getContextType());
        assertNull(managed.getTenantRole());
        assertEquals(
                Arrays.asList("AUDIT_READ", "TENANT_MANAGE"),
                managed.getPlatformAuthorities());
    }

    @Test
    void rejectsMissingVersionDuplicateScalarAndUnknownContext() {
        MockHttpServletRequest missing = tenantRequest();
        missing.removeHeader(
                TrustedRequestHeaders.MEMBER_CONTEXT_VERSION);
        assertStatus(401, missing);

        MockHttpServletRequest duplicate = tenantRequest();
        duplicate.addHeader(TrustedRequestHeaders.USER_ID, "18");
        assertStatus(401, duplicate);

        MockHttpServletRequest unknown = baseUserRequest(
                TrustedRequestHeaders.SOURCE_GATEWAY,
                "1",
                "DEFAULT_TENANT");
        assertStatus(401, unknown);
    }

    @Test
    void rejectsRoleAndMembershipForgeryAsPermissionErrors() {
        MockHttpServletRequest forgedTenant = baseUserRequest(
                TrustedRequestHeaders.SOURCE_GATEWAY,
                "0",
                "TENANT");
        addTenant(forgedTenant, true);
        assertStatus(403, forgedTenant);

        MockHttpServletRequest forgedManaged = baseUserRequest(
                TrustedRequestHeaders.SOURCE_GATEWAY,
                "0",
                "PLATFORM_TENANT");
        addTenant(forgedManaged, true);
        forgedManaged.addHeader(
                TrustedRequestHeaders.PLATFORM_AUTHORITIES,
                "TENANT_MANAGE");
        assertStatus(403, forgedManaged);

        MockHttpServletRequest mixedTenant = tenantRequest();
        mixedTenant.addHeader(
                TrustedRequestHeaders.PLATFORM_AUTHORITIES,
                "TENANT_MANAGE");
        assertStatus(403, mixedTenant);
    }

    @Test
    void rejectsForgedHeadersWithoutTrustedSourceMarker() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/devices");
        request.addHeader(TrustedRequestHeaders.USER_ID, "17");

        assertStatus(401, request);
    }

    private MockHttpServletRequest platformRequest() {
        MockHttpServletRequest request = baseUserRequest(
                TrustedRequestHeaders.SOURCE_GATEWAY,
                "0",
                "PLATFORM");
        request.addHeader(
                TrustedRequestHeaders.PLATFORM_AUTHORITIES,
                "TENANT_MANAGE");
        return request;
    }

    private MockHttpServletRequest tenantRequest() {
        MockHttpServletRequest request = baseUserRequest(
                TrustedRequestHeaders.SOURCE_GATEWAY,
                "1",
                "TENANT");
        addTenant(request, true);
        return request;
    }

    private MockHttpServletRequest baseUserRequest(
            String source,
            String role,
            String contextType) {
        MockHttpServletRequest request = trustedRequest(source);
        request.addHeader(TrustedRequestHeaders.USER_ID, "17");
        request.addHeader(
                TrustedRequestHeaders.USER_NAME,
                "trusted-user");
        request.addHeader(TrustedRequestHeaders.USER_ROLE, role);
        request.addHeader(
                TrustedRequestHeaders.SESSION_ID,
                "session-00000001");
        request.addHeader(
                TrustedRequestHeaders.TOKEN_ID,
                "token-id-00000001");
        request.addHeader(
                TrustedRequestHeaders.CONTEXT_TYPE,
                contextType);
        return request;
    }

    private void addTenant(
            MockHttpServletRequest request,
            boolean member) {
        request.addHeader(TrustedRequestHeaders.TENANT_ID, "3");
        request.addHeader(
                TrustedRequestHeaders.TENANT_CODE,
                "tenant-a");
        request.addHeader(
                TrustedRequestHeaders.TENANT_CONTEXT_VERSION,
                "4");
        if (member) {
            request.addHeader(
                    TrustedRequestHeaders.TENANT_ROLE,
                    "TENANT_ADMIN");
            request.addHeader(
                    TrustedRequestHeaders.MEMBER_CONTEXT_VERSION,
                    "5");
        }
    }

    private MockHttpServletRequest trustedRequest(String source) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/devices");
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                source);
        request.setAttribute(
                RequestId.REQUEST_ATTRIBUTE,
                REQUEST_ID);
        return request;
    }

    private void assertStatus(
            int expectedStatus,
            MockHttpServletRequest request) {
        TrustedRequestContextException exception = assertThrows(
                TrustedRequestContextException.class,
                () -> resolver.resolve(request));
        assertEquals(expectedStatus, exception.getHttpStatus());
        assertNotNull(exception.getErrorKey());
    }
}
