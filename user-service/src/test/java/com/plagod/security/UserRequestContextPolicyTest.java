package com.plagod.security;

import com.plagod.exception.ApiStatusException;
import com.plagod.request.RequestId;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UserRequestContextPolicyTest {

    private final UserRequestContextPolicy policy =
            new UserRequestContextPolicy(
                    new TrustedRequestContextResolver());

    @Test
    void tenantActorUsesFrozenUserAndTenantContext() {
        MockHttpServletRequest request = tenantRequest(7L, 31L);

        TrustedRequestContext context =
                policy.requireTenantBoundActor(request);

        assertEquals(7L, context.getUserId());
        assertEquals(31L, policy.tenantId(context));
    }

    @Test
    void platformCannotDirectlyEnterTenantBoundResource() {
        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> policy.requireTenantBoundActor(platformRequest()));

        assertEquals(403, exception.getHttpStatus());
    }

    @Test
    void internalServiceWithoutUserActorCannotReadUserResource() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedRequestHeaders.SOURCE_INTERNAL);
        request.addHeader(
                RequestId.HEADER_NAME,
                "request-internal-0001");

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> policy.requireTenantBoundActor(request));

        assertEquals(403, exception.getHttpStatus());
    }

    @Test
    void forgedHeadersWithoutTrustedSourceAreRejected() {
        MockHttpServletRequest request = tenantRequest(7L, 31L);
        request.removeAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE);

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> policy.requireTenantBoundActor(request));

        assertEquals(401, exception.getHttpStatus());
    }

    @Test
    void otherUserResourceIsHiddenAsNotFound() {
        TrustedRequestContext context =
                policy.requireUserActor(tenantRequest(7L, 31L));

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> policy.requireSelf(context, 8L));

        assertEquals(404, exception.getHttpStatus());
    }

    @Test
    void tenantAdminCanManageCurrentTenantEntitlements() {
        TrustedRequestContext context =
                policy.requireEntitlementAdminActor(
                        tenantAdminRequest(7L, 31L));

        assertEquals(31L, policy.tenantId(context));
        assertEquals("TENANT_ADMIN", context.getTenantRole());
    }

    @Test
    void tenantMemberCannotManageTenantEntitlements() {
        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> policy.requireEntitlementAdminActor(
                        tenantRequest(7L, 31L)));

        assertEquals(403, exception.getHttpStatus());
    }

    @Test
    void platformTenantCanManageExplicitTargetTenant() {
        TrustedRequestContext context =
                policy.requireEntitlementAdminActor(
                        platformTenantRequest(1L, 31L));

        assertEquals(31L, policy.tenantId(context));
        assertEquals(
                TrustedContextType.PLATFORM_TENANT,
                context.getContextType());
    }

    public static MockHttpServletRequest tenantRequest(
            Long userId,
            Long tenantId) {
        return tenantRequest(
                userId,
                tenantId,
                2,
                "MEMBER");
    }

    public static MockHttpServletRequest tenantAdminRequest(
            Long userId,
            Long tenantId) {
        return tenantRequest(
                userId,
                tenantId,
                1,
                "TENANT_ADMIN");
    }

    private static MockHttpServletRequest tenantRequest(
            Long userId,
            Long tenantId,
            int globalRole,
            String tenantRole) {
        MockHttpServletRequest request = baseUserRequest(
                userId,
                globalRole,
                TrustedContextType.TENANT);
        request.addHeader(
                TrustedRequestHeaders.TENANT_ID,
                String.valueOf(tenantId));
        request.addHeader(
                TrustedRequestHeaders.TENANT_CODE,
                "tenant-" + tenantId);
        request.addHeader(
                TrustedRequestHeaders.TENANT_ROLE,
                tenantRole);
        request.addHeader(
                TrustedRequestHeaders.TENANT_CONTEXT_VERSION,
                "3");
        request.addHeader(
                TrustedRequestHeaders.MEMBER_CONTEXT_VERSION,
                "4");
        return request;
    }

    public static MockHttpServletRequest platformTenantRequest(
            Long userId,
            Long tenantId) {
        MockHttpServletRequest request = baseUserRequest(
                userId,
                0,
                TrustedContextType.PLATFORM_TENANT);
        request.addHeader(
                TrustedRequestHeaders.TENANT_ID,
                String.valueOf(tenantId));
        request.addHeader(
                TrustedRequestHeaders.TENANT_CODE,
                "tenant-" + tenantId);
        request.addHeader(
                TrustedRequestHeaders.TENANT_CONTEXT_VERSION,
                "3");
        request.addHeader(
                TrustedRequestHeaders.PLATFORM_AUTHORITIES,
                "TENANT_MANAGE");
        return request;
    }

    public static MockHttpServletRequest platformRequest() {
        MockHttpServletRequest request = baseUserRequest(
                1L,
                0,
                TrustedContextType.PLATFORM);
        request.addHeader(
                TrustedRequestHeaders.PLATFORM_AUTHORITIES,
                "TENANT_MANAGE");
        return request;
    }

    private static MockHttpServletRequest baseUserRequest(
            Long userId,
            int role,
            TrustedContextType contextType) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedRequestHeaders.SOURCE_GATEWAY);
        request.addHeader(
                TrustedRequestHeaders.USER_ID,
                String.valueOf(userId));
        request.addHeader(
                TrustedRequestHeaders.USER_ROLE,
                String.valueOf(role));
        request.addHeader(
                TrustedRequestHeaders.SESSION_ID,
                "session-" + userId);
        request.addHeader(
                TrustedRequestHeaders.TOKEN_ID,
                "token-" + userId);
        request.addHeader(
                TrustedRequestHeaders.CONTEXT_TYPE,
                contextType.name());
        request.addHeader(
                RequestId.HEADER_NAME,
                "request-context-0001");
        return request;
    }
}
