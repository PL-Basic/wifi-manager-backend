package com.plagod.configuration;

import com.plagod.client.DeviceServiceClient;
import com.plagod.client.TenantServiceClient;
import com.plagod.controller.AdminDeviceController;
import com.plagod.controller.AdminPlatformTenantController;
import com.plagod.exception.ApiStatusException;
import com.plagod.request.RequestId;
import com.plagod.security.TrustedRequestContextResolver;
import com.plagod.security.TrustedRequestHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class AdminRequestScopeInterceptorTest {

    private final AdminRequestScopeInterceptor interceptor =
            new AdminRequestScopeInterceptor(
                    new TrustedRequestContextResolver());

    @Test
    void tenantAndPlatformTenantCanUseTenantResourceController()
            throws Exception {
        assertTrue(preHandle(
                tenantRequest(),
                tenantResourceHandler()));
        assertTrue(preHandle(
                platformTenantRequest(),
                tenantResourceHandler()));
    }

    @Test
    void platformCannotDirectlyUseTenantResourceController()
            throws Exception {
        assertForbidden(
                platformRequest(),
                tenantResourceHandler());
    }

    @Test
    void tenantAndPlatformTenantCannotUsePlatformController()
            throws Exception {
        assertForbidden(
                tenantRequest(),
                platformHandler());
        assertForbidden(
                platformTenantRequest(),
                platformHandler());
    }

    @Test
    void pureInternalServiceCannotUseUserBffController()
            throws Exception {
        MockHttpServletRequest request = baseRequest(
                TrustedRequestHeaders.SOURCE_INTERNAL);

        assertForbidden(request, tenantResourceHandler());
        assertForbidden(request, platformHandler());
    }

    @Test
    void browserHeadersWithoutTrustedSourceCannotBuildContext()
            throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.addHeader(
                RequestId.HEADER_NAME,
                "request-id-00000001");
        request.addHeader(TrustedRequestHeaders.USER_ID, "9");
        request.addHeader(
                TrustedRequestHeaders.USER_ROLE,
                "1");
        request.addHeader(
                TrustedRequestHeaders.SESSION_ID,
                "session-00000001");
        request.addHeader(
                TrustedRequestHeaders.TOKEN_ID,
                "token-0000000001");
        request.addHeader(
                TrustedRequestHeaders.CONTEXT_TYPE,
                "TENANT");
        request.addHeader(
                TrustedRequestHeaders.TENANT_ID,
                "101");
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

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> preHandle(
                        request,
                        tenantResourceHandler()));
        assertEquals(401, exception.getHttpStatus());
    }

    @Test
    void platformCanUseExplicitPlatformController()
            throws Exception {
        assertTrue(preHandle(
                platformRequest(),
                platformHandler()));
    }

    private boolean preHandle(
            MockHttpServletRequest request,
            HandlerMethod handler) throws Exception {
        return interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                handler);
    }

    private void assertForbidden(
            MockHttpServletRequest request,
            HandlerMethod handler) {
        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> preHandle(request, handler));
        assertEquals(403, exception.getHttpStatus());
    }

    private HandlerMethod tenantResourceHandler()
            throws NoSuchMethodException {
        AdminDeviceController controller =
                new AdminDeviceController();
        return new HandlerMethod(
                controller,
                AdminDeviceController.class.getMethod(
                        "getDevice",
                        Long.class));
    }

    private HandlerMethod platformHandler()
            throws NoSuchMethodException {
        AdminPlatformTenantController controller =
                new AdminPlatformTenantController(
                        mock(TenantServiceClient.class));
        return new HandlerMethod(
                controller,
                AdminPlatformTenantController.class.getMethod(
                        "pageTenants",
                        long.class,
                        long.class,
                        String.class));
    }

    private MockHttpServletRequest tenantRequest() {
        MockHttpServletRequest request = userRequest(1, "TENANT");
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

    private MockHttpServletRequest platformRequest() {
        return userRequest(0, "PLATFORM");
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
            int globalRole,
            String contextType) {
        MockHttpServletRequest request = baseRequest(
                TrustedRequestHeaders.SOURCE_GATEWAY);
        request.addHeader(TrustedRequestHeaders.USER_ID, "9");
        request.addHeader(
                TrustedRequestHeaders.USER_ROLE,
                String.valueOf(globalRole));
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
}
