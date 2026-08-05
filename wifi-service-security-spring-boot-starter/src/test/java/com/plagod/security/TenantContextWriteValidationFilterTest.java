package com.plagod.security;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.TenantContextValidationRequest;
import com.plagod.vo.tenant.TenantContextValidationVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantContextWriteValidationFilterTest {

    @Test
    void validatesTenantWriteAndContinuesChain() throws Exception {
        AtomicReference<TenantContextValidationRequest> captured = new AtomicReference<>();
        TenantContextWriteValidationFilter filter = new TenantContextWriteValidationFilter(
                request -> {
                    captured.set(request);
                    return allowed();
                },
                "device-service");
        MockHttpServletRequest request = tenantRequest("POST", "/devices");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, chain(invoked));

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
        assertEquals("17", captured.get().getUserId());
        assertEquals(Integer.valueOf(1), captured.get().getGlobalRole());
        assertEquals("TENANT", captured.get().getContextType());
        assertEquals("3", captured.get().getTenantId());
        assertEquals(Long.valueOf(4), captured.get().getContextVersion());
        assertEquals(Long.valueOf(5), captured.get().getMemberContextVersion());
        assertEquals(Arrays.asList("TENANT_MANAGE", "DEVICE_WRITE"),
                captured.get().getAuthorities());
        assertTrue(captured.get().getWriteRequest());
        assertFalse(captured.get().getLegacyToken());
    }

    @Test
    void platformTenantWriteDoesNotRequireMemberSnapshot() throws Exception {
        AtomicReference<TenantContextValidationRequest> captured = new AtomicReference<>();
        TenantContextWriteValidationFilter filter = new TenantContextWriteValidationFilter(
                request -> {
                    captured.set(request);
                    return allowed();
                },
                "admin-service");
        MockHttpServletRequest request = tenantRequest("DELETE", "/admin/users/8");
        request.removeHeader(TrustedHeaderNames.MEMBER_CONTEXT_VERSION);
        request.removeHeader(TrustedHeaderNames.TENANT_ROLE);
        request.removeHeader(TrustedHeaderNames.CONTEXT_TYPE);
        request.addHeader(TrustedHeaderNames.CONTEXT_TYPE, "PLATFORM_TENANT");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain(new AtomicBoolean()));

        assertEquals("PLATFORM_TENANT", captured.get().getContextType());
        assertNull(captured.get().getMemberContextVersion());
        assertNull(captured.get().getTenantRole());
    }

    @Test
    void safeAuthAndContextFreeInternalRequestsDoNotCallValidator() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TenantContextWriteValidationFilter filter = new TenantContextWriteValidationFilter(
                request -> {
                    calls.incrementAndGet();
                    return allowed();
                },
                "monitor-service");

        filter.doFilter(
                tenantRequest("GET", "/alerts"),
                new MockHttpServletResponse(),
                chain(new AtomicBoolean()));
        MockHttpServletRequest internalRequest =
                new MockHttpServletRequest("POST", "/internal/alerts/evaluate");
        internalRequest.setAttribute(
                TrustedHeaderNames.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedHeaderNames.SOURCE_INTERNAL);
        filter.doFilter(
                internalRequest,
                new MockHttpServletResponse(),
                chain(new AtomicBoolean()));
        filter.doFilter(
                tenantRequest("POST", "/auth/logout"),
                new MockHttpServletResponse(),
                chain(new AtomicBoolean()));

        assertEquals(0, calls.get());
    }

    @Test
    void internalBffWriteWithPropagatedTenantContextIsValidated() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TenantContextWriteValidationFilter filter = new TenantContextWriteValidationFilter(
                request -> {
                    calls.incrementAndGet();
                    return allowed();
                },
                "user-service");
        MockHttpServletRequest request =
                tenantRequest("PUT", "/internal/admin/users/17/status");
        request.setAttribute(
                TrustedHeaderNames.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedHeaderNames.SOURCE_INTERNAL);

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                chain(new AtomicBoolean()));

        assertEquals(1, calls.get());
    }

    @Test
    void tenantServiceNeverValidatesItself() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TenantContextWriteValidationFilter filter = new TenantContextWriteValidationFilter(
                request -> {
                    calls.incrementAndGet();
                    return allowed();
                },
                "tenant-service");
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(
                tenantRequest("POST", "/tenants/3"),
                new MockHttpServletResponse(),
                chain(invoked));

        assertEquals(0, calls.get());
        assertTrue(invoked.get());
    }

    @Test
    void validationDependencyFailureReturns503() throws Exception {
        TenantContextWriteValidationFilter filter = new TenantContextWriteValidationFilter(
                request -> {
                    throw new IllegalStateException("tenant-service unavailable");
                },
                "user-service");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(tenantRequest("PATCH", "/users/17"), response, chain(invoked));

        assertFalse(invoked.get());
        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":503"));
    }

    @Test
    void invalidValidationResponseReturns503() throws Exception {
        TenantContextWriteValidationFilter filter = new TenantContextWriteValidationFilter(
                request -> ApiResponse.fail(500, "unexpected"),
                "user-service");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(tenantRequest("POST", "/orders"), response, chain(invoked));

        assertFalse(invoked.get());
        assertEquals(503, response.getStatus());
    }

    @Test
    void malformedTrustedContextFailsClosedBeforeBusinessCode() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TenantContextWriteValidationFilter filter = new TenantContextWriteValidationFilter(
                request -> {
                    calls.incrementAndGet();
                    return allowed();
                },
                "device-service");
        MockHttpServletRequest request = tenantRequest("POST", "/devices");
        request.removeHeader(TrustedHeaderNames.TENANT_CONTEXT_VERSION);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, chain(invoked));

        assertEquals(0, calls.get());
        assertFalse(invoked.get());
        assertEquals(401, response.getStatus());
    }

    private MockHttpServletRequest tenantRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setAttribute(
                TrustedHeaderNames.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedHeaderNames.SOURCE_GATEWAY);
        request.addHeader(TrustedHeaderNames.USER_ID, "17");
        request.addHeader(TrustedHeaderNames.USER_ROLE, "1");
        request.addHeader(TrustedHeaderNames.CONTEXT_TYPE, "TENANT");
        request.addHeader(TrustedHeaderNames.TENANT_ID, "3");
        request.addHeader(TrustedHeaderNames.TENANT_CODE, "default-tenant");
        request.addHeader(TrustedHeaderNames.TENANT_ROLE, "TENANT_ADMIN");
        request.addHeader(TrustedHeaderNames.TENANT_CONTEXT_VERSION, "4");
        request.addHeader(TrustedHeaderNames.MEMBER_CONTEXT_VERSION, "5");
        request.addHeader(
                TrustedHeaderNames.PLATFORM_AUTHORITIES,
                "TENANT_MANAGE, DEVICE_WRITE, TENANT_MANAGE");
        return request;
    }

    private ApiResponse<TenantContextValidationVO> allowed() {
        TenantContextValidationVO data = new TenantContextValidationVO();
        data.setAllowed(true);
        return ApiResponse.success(data);
    }

    private FilterChain chain(AtomicBoolean invoked) {
        return (request, response) -> invoked.set(true);
    }
}
