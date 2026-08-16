package com.plagod.controller;

import com.plagod.client.UserServiceClient;
import com.plagod.configuration.AdminRequestScopeInterceptor;
import com.plagod.exception.ApiStatusException;
import com.plagod.request.RequestId;
import com.plagod.security.TrustedRequestContextResolver;
import com.plagod.security.TrustedRequestHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminUserControllerAuthorizationTest {

    private final UserServiceClient client =
            mock(UserServiceClient.class);
    private final AdminUserController controller =
            new AdminUserController();
    private final AdminRequestScopeInterceptor interceptor =
            new AdminRequestScopeInterceptor(
                    new TrustedRequestContextResolver());

    AdminUserControllerAuthorizationTest() {
        ReflectionTestUtils.setField(
                controller,
                "userServiceClient",
                client);
    }

    @Test
    void platformSuperAdminIsAllowed() throws Exception {
        assertTrue(preHandle(platformRequest()));
    }

    @Test
    void tenantAdminIsRejectedBeforeCallingUserService() {
        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> preHandle(tenantRequest()));

        assertEquals(403, exception.getHttpStatus());
        assertEquals(403, exception.getCode());
        verifyNoInteractions(client);
    }

    @Test
    void missingRoleIsRejectedBeforeCallingUserService() {
        MockHttpServletRequest request =
                baseUserRequest("PLATFORM");

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> preHandle(request));

        assertEquals(401, exception.getHttpStatus());
        verifyNoInteractions(client);
    }

    private boolean preHandle(
            MockHttpServletRequest request) throws Exception {
        Method method = AdminUserController.class.getMethod(
                "pageUsers",
                Long.class,
                Long.class,
                String.class);
        return interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(controller, method));
    }

    private MockHttpServletRequest platformRequest() {
        MockHttpServletRequest request =
                baseUserRequest("PLATFORM");
        request.addHeader(TrustedRequestHeaders.USER_ROLE, "0");
        return request;
    }

    private MockHttpServletRequest tenantRequest() {
        MockHttpServletRequest request =
                baseUserRequest("TENANT");
        request.addHeader(TrustedRequestHeaders.USER_ROLE, "1");
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

    private MockHttpServletRequest baseUserRequest(
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
