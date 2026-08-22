package com.plagod.controller;

import com.plagod.client.TenantServiceClient;
import com.plagod.configuration.AdminRequestScopeInterceptor;
import com.plagod.dto.tenant.TenantStatusRequest;
import com.plagod.dto.tenant.TenantUpdateRequest;
import com.plagod.exception.ApiStatusException;
import com.plagod.request.RequestId;
import com.plagod.security.TrustedRequestContextResolver;
import com.plagod.security.TrustedRequestHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminPlatformTenantControllerTest {

    private final TenantServiceClient client =
            mock(TenantServiceClient.class);
    private final AdminPlatformTenantController controller =
            new AdminPlatformTenantController(client);
    private final AdminRequestScopeInterceptor interceptor =
            new AdminRequestScopeInterceptor(
                    new TrustedRequestContextResolver());

    @Test
    void platformCanUseCatalogMethods() throws Exception {
        assertAllowed(platformRequest(), method(
                "pageTenants",
                long.class,
                long.class,
                String.class));
        assertAllowed(platformRequest(), method(
                "createTenant",
                com.plagod.dto.tenant.TenantCreateRequest.class));
        assertAllowed(platformRequest(), method("listPlans"));
    }

    @Test
    void platformCannotUseManagedTenantMethods()
            throws Exception {
        assertForbidden(platformRequest(), method(
                "getTenant",
                String.class));
        assertForbidden(platformRequest(), method(
                "pageMembers",
                String.class,
                long.class,
                long.class));
        assertForbidden(platformRequest(), method(
                "updateTenant",
                String.class,
                TenantUpdateRequest.class));
        assertForbidden(platformRequest(), method(
                "updateStatus",
                String.class,
                TenantStatusRequest.class));

        verifyNoInteractions(client);
    }

    @Test
    void platformTenantCanUseManagedTenantMethods()
            throws Exception {
        assertAllowed(platformTenantRequest(), method(
                "getTenant",
                String.class));
        assertAllowed(platformTenantRequest(), method(
                "pageMembers",
                String.class,
                long.class,
                long.class));
        assertAllowed(platformTenantRequest(), method(
                "updateTenant",
                String.class,
                TenantUpdateRequest.class));
        assertAllowed(platformTenantRequest(), method(
                "updateStatus",
                String.class,
                TenantStatusRequest.class));
    }

    @Test
    void tenantCannotUsePlatformManagementEntry()
            throws Exception {
        assertForbidden(tenantRequest(), method(
                "getTenant",
                String.class));
        assertForbidden(tenantRequest(), method(
                "pageMembers",
                String.class,
                long.class,
                long.class));
        assertForbidden(tenantRequest(), method(
                "pageTenants",
                long.class,
                long.class,
                String.class));

        verifyNoInteractions(client);
    }

    @Test
    void platformTenantCannotUsePlatformCatalogMethods()
            throws Exception {
        assertForbidden(platformTenantRequest(), method(
                "pageTenants",
                long.class,
                long.class,
                String.class));
        assertForbidden(platformTenantRequest(), method(
                "createTenant",
                com.plagod.dto.tenant.TenantCreateRequest.class));
        assertForbidden(platformTenantRequest(), method("listPlans"));

        verifyNoInteractions(client);
    }

    @Test
    void managedTenantPathIsForwardedForDownstreamContextCheck()
            throws Exception {
        MockHttpServletRequest request = platformTenantRequest();
        assertAllowed(request, method("getTenant", String.class));
        controller.getTenant("101");

        TenantUpdateRequest updateRequest =
                new TenantUpdateRequest();
        assertAllowed(request, method(
                "updateTenant",
                String.class,
                TenantUpdateRequest.class));
        controller.updateTenant("101", updateRequest);

        TenantStatusRequest statusRequest =
                new TenantStatusRequest();
        assertAllowed(request, method(
                "updateStatus",
                String.class,
                TenantStatusRequest.class));
        controller.updateStatus("101", statusRequest);

        assertAllowed(request, method(
                "pageMembers",
                String.class,
                long.class,
                long.class));
        controller.pageMembers("101", 1L, 20L);

        verify(client).getTenant("101");
        verify(client).updateTenant("101", updateRequest);
        verify(client).updateStatus("101", statusRequest);
        verify(client).pageMembers("101", 1L, 20L);
    }

    private Method method(
            String name,
            Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return AdminPlatformTenantController.class.getMethod(
                name,
                parameterTypes);
    }

    private void assertAllowed(
            MockHttpServletRequest request,
            Method method) throws Exception {
        assertTrue(interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(controller, method)));
    }

    private void assertForbidden(
            MockHttpServletRequest request,
            Method method) {
        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> interceptor.preHandle(
                        request,
                        new MockHttpServletResponse(),
                        new HandlerMethod(controller, method)));
        assertEquals(403, exception.getHttpStatus());
    }

    private MockHttpServletRequest platformRequest() {
        return userRequest(0, "PLATFORM");
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
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedRequestHeaders.SOURCE_GATEWAY);
        request.addHeader(
                RequestId.HEADER_NAME,
                "request-id-00000001");
        request.addHeader(TrustedRequestHeaders.USER_ID, "9");
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
}
