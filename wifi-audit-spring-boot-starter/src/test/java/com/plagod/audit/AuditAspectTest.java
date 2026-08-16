package com.plagod.audit;

import com.plagod.security.TrustedHeaderNames;
import com.plagod.security.TrustedRequestFilter;
import com.plagod.security.TrustedRequestProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditAspectTest {

    private static final String GATEWAY_TOKEN =
            "audit-test-gateway-token-value";
    private static final String INTERNAL_TOKEN =
            "audit-test-internal-token-value";

    @Test
    void trustedAnonymousPlatformActionAppendsWithoutContext()
            throws Throwable {
        AuditWriter writer = mock(AuditWriter.class);
        AuditAspect aspect = new AuditAspect(writer);
        bindAuthenticatedRequest(null, null);

        try {
            assertEquals(
                    "done",
                    aspect.around(joinPoint(
                            "platformAudited",
                            new Class<?>[0],
                            new Object[0],
                            "done")));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        AuditWriteRecord record = capturedRecord(writer);
        assertNull(record.getTenantId());
        assertEquals("PLATFORM", record.getScopeType());
        assertEquals("test.platform", record.getAction());
    }

    @Test
    void platformActionRemainsPlatformInPlatformTenantContext()
            throws Throwable {
        AuditWriter writer = mock(AuditWriter.class);
        AuditAspect aspect = new AuditAspect(writer);
        bindAuthenticatedRequest("PLATFORM_TENANT", "9");

        try {
            assertEquals(
                    "done",
                    aspect.around(joinPoint(
                            "platformAudited",
                            new Class<?>[0],
                            new Object[0],
                            "done")));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        AuditWriteRecord record = capturedRecord(writer);
        assertNull(record.getTenantId());
        assertEquals("PLATFORM", record.getScopeType());
    }

    @Test
    void tenantRequestAppendsTrustedTenantScope() throws Throwable {
        AuditWriter writer = mock(AuditWriter.class);
        AuditAspect aspect = new AuditAspect(writer);
        bindAuthenticatedRequest("TENANT", "7");

        try {
            assertEquals(
                    "done",
                    aspect.around(joinPoint(
                            "tenantRequestAudited",
                            new Class<?>[0],
                            new Object[0],
                            "done")));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        AuditWriteRecord record = capturedRecord(writer);
        assertEquals(7L, record.getTenantId());
        assertEquals("TENANT", record.getScopeType());
    }

    @Test
    void contextScopeFollowsOnlyTrustedRequestContext()
            throws Throwable {
        assertContextScope("PLATFORM", null, null, "PLATFORM");
        assertContextScope(
                "PLATFORM_TENANT",
                "9",
                9L,
                "TENANT");
    }

    @Test
    void asyncTenantActionAppendsFromMarkedArgumentWithoutRequest()
            throws Throwable {
        AuditWriter writer = mock(AuditWriter.class);
        AuditAspect aspect = new AuditAspect(writer);
        RequestContextHolder.resetRequestAttributes();

        assertEquals(
                "done",
                aspect.around(joinPoint(
                        "tenantArgumentAudited",
                        new Class<?>[]{Long.class},
                        new Object[]{11L},
                        "done")));

        AuditWriteRecord record = capturedRecord(writer);
        assertEquals(11L, record.getTenantId());
        assertEquals("TENANT", record.getScopeType());
    }

    @Test
    void untrustedOrInvalidScopeDoesNotAppendAudit() throws Throwable {
        assertUntrustedHeadersDoNotReachAuditAspect();
        assertInvalidDoesNotAppend(
                "platformAudited",
                new Class<?>[0],
                new Object[0],
                null,
                null,
                false);
        assertInvalidDoesNotAppend(
                "tenantRequestAudited",
                new Class<?>[0],
                new Object[0],
                null,
                null,
                false);
        assertInvalidDoesNotAppend(
                "tenantArgumentAudited",
                new Class<?>[]{Long.class},
                new Object[]{null},
                null,
                null,
                false);
        assertInvalidDoesNotAppend(
                "tenantArgumentAudited",
                new Class<?>[]{Long.class},
                new Object[]{0L},
                null,
                null,
                false);
        assertInvalidDoesNotAppend(
                "tenantArgumentAudited",
                new Class<?>[]{Long.class},
                new Object[]{-1L},
                null,
                null,
                false);
        assertInvalidDoesNotAppend(
                "tenantArgumentAudited",
                new Class<?>[]{Long.class},
                new Object[]{7L},
                "TENANT",
                "8",
                true);
        assertInvalidDoesNotAppend(
                "tenantArgumentAudited",
                new Class<?>[]{Long.class},
                new Object[]{7L},
                "PLATFORM",
                null,
                true);
        assertInvalidDoesNotAppend(
                "tenantWithoutMarkerAudited",
                new Class<?>[]{Long.class},
                new Object[]{7L},
                null,
                null,
                false);
    }

    private void assertContextScope(String contextType,
                                    String tenantId,
                                    Long expectedTenantId,
                                    String expectedScopeType)
            throws Throwable {
        AuditWriter writer = mock(AuditWriter.class);
        AuditAspect aspect = new AuditAspect(writer);
        bindAuthenticatedRequest(contextType, tenantId);

        try {
            assertEquals(
                    "done",
                    aspect.around(joinPoint(
                            "contextAudited",
                            new Class<?>[0],
                            new Object[0],
                            "done")));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        AuditWriteRecord record = capturedRecord(writer);
        assertEquals(expectedTenantId, record.getTenantId());
        assertEquals(expectedScopeType, record.getScopeType());
    }

    @Test
    void auditFailureDoesNotReplaceSuccessfulBusinessReturn()
            throws Throwable {
        AuditWriter writer = mock(AuditWriter.class);
        doThrow(new IllegalStateException("write failed"))
                .when(writer).append(
                        org.mockito.ArgumentMatchers.any(
                                AuditWriteRecord.class));
        AuditAspect aspect = new AuditAspect(writer);
        bindAuthenticatedRequest(null, null);

        Object result;
        try {
            result = aspect.around(joinPoint(
                    "platformAudited",
                    new Class<?>[0],
                    new Object[0],
                    "done"));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        assertEquals("done", result);
    }

    @Test
    void businessFailureDoesNotAppendAudit() throws Throwable {
        AuditWriter writer = mock(AuditWriter.class);
        AuditAspect aspect = new AuditAspect(writer);
        ProceedingJoinPoint joinPoint = joinPoint(
                "platformAudited",
                new Class<?>[0],
                new Object[0],
                null);
        when(joinPoint.proceed())
                .thenThrow(new IllegalStateException("business failed"));

        assertThrows(
                IllegalStateException.class,
                () -> aspect.around(joinPoint));
        verify(writer, never()).append(
                org.mockito.ArgumentMatchers.any(
                        AuditWriteRecord.class));
    }

    private void assertInvalidDoesNotAppend(
            String methodName,
            Class<?>[] parameterTypes,
            Object[] arguments,
            String contextType,
            String tenantId,
            boolean bindRequest) throws Throwable {
        AuditWriter writer = mock(AuditWriter.class);
        AuditAspect aspect = new AuditAspect(writer);
        if (bindRequest) {
            bindAuthenticatedRequest(contextType, tenantId);
        } else {
            RequestContextHolder.resetRequestAttributes();
        }

        try {
            assertEquals(
                    "done",
                    aspect.around(joinPoint(
                            methodName,
                            parameterTypes,
                            arguments,
                            "done")));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        verify(writer, never()).append(
                org.mockito.ArgumentMatchers.any(AuditWriteRecord.class));
    }

    private void assertUntrustedHeadersDoNotReachAuditAspect()
            throws Exception {
        AuditWriter writer = mock(AuditWriter.class);
        AuditAspect aspect = new AuditAspect(writer);
        MockHttpServletRequest request =
                requestWithContext("TENANT", "7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean aspectInvoked = new AtomicBoolean();

        trustedRequestFilter().doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                    aspectInvoked.set(true);
                    try {
                        aspect.around(joinPoint(
                                "tenantRequestAudited",
                                new Class<?>[0],
                                new Object[0],
                                "done"));
                    } catch (Throwable throwable) {
                        throw new IllegalStateException(throwable);
                    }
                });

        assertFalse(aspectInvoked.get());
        assertEquals(401, response.getStatus());
        verify(writer, never()).append(
                org.mockito.ArgumentMatchers.any(AuditWriteRecord.class));
    }

    private AuditWriteRecord capturedRecord(AuditWriter writer) {
        ArgumentCaptor<AuditWriteRecord> record =
                ArgumentCaptor.forClass(AuditWriteRecord.class);
        verify(writer).append(record.capture());
        return record.getValue();
    }

    private void bindAuthenticatedRequest(
            String contextType,
            String tenantId) throws Exception {
        MockHttpServletRequest request =
                requestWithContext(contextType, tenantId);
        request.addHeader(TrustedHeaderNames.GATEWAY_TOKEN, GATEWAY_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean();

        trustedRequestFilter().doFilter(
                request,
                response,
                (servletRequest, servletResponse) ->
                        chainInvoked.set(true));

        assertTrue(chainInvoked.get());
        assertEquals(
                TrustedHeaderNames.SOURCE_GATEWAY,
                request.getAttribute(
                        TrustedHeaderNames.TRUSTED_SOURCE_ATTRIBUTE));
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));
    }

    private MockHttpServletRequest requestWithContext(
            String contextType,
            String tenantId) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/audit-target");
        if (contextType != null) {
            request.addHeader(TrustedHeaderNames.CONTEXT_TYPE, contextType);
        }
        if (tenantId != null) {
            request.addHeader(TrustedHeaderNames.TENANT_ID, tenantId);
        }
        return request;
    }

    private TrustedRequestFilter trustedRequestFilter() {
        TrustedRequestProperties properties =
                new TrustedRequestProperties();
        properties.setGatewayToken(GATEWAY_TOKEN);
        properties.setInternalToken(INTERNAL_TOKEN);
        properties.setInternalTokenRequired(true);
        return new TrustedRequestFilter(properties);
    }

    private ProceedingJoinPoint joinPoint(
            String methodName,
            Class<?>[] parameterTypes,
            Object[] arguments,
            Object result) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = AuditFixture.class.getDeclaredMethod(
                methodName,
                parameterTypes);
        AuditFixture target = new AuditFixture();
        when(joinPoint.proceed()).thenReturn(result);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getArgs()).thenReturn(arguments);
        when(signature.getMethod()).thenReturn(method);
        return joinPoint;
    }

    private static class AuditFixture {

        @Audited(
                action = "test.platform",
                scope = Audited.Scope.PLATFORM,
                tenantIdSource = Audited.TenantIdSource.REQUEST,
                includeArgs = false,
                includeResult = false)
        public void platformAudited() {
        }

        @Audited(
                action = "test.tenant.request",
                scope = Audited.Scope.TENANT,
                tenantIdSource = Audited.TenantIdSource.REQUEST,
                includeArgs = false,
                includeResult = false)
        public void tenantRequestAudited() {
        }

        @Audited(
                action = "test.tenant.argument",
                scope = Audited.Scope.TENANT,
                tenantIdSource = Audited.TenantIdSource.ARGUMENT,
                includeArgs = false,
                includeResult = false)
        public void tenantArgumentAudited(@AuditTenantId Long tenantId) {
        }

        @Audited(
                action = "test.tenant.no-marker",
                scope = Audited.Scope.TENANT,
                tenantIdSource = Audited.TenantIdSource.ARGUMENT,
                includeArgs = false,
                includeResult = false)
        public void tenantWithoutMarkerAudited(Long tenantId) {
        }

        @Audited(
                action = "test.context",
                scope = Audited.Scope.CONTEXT,
                tenantIdSource = Audited.TenantIdSource.REQUEST,
                includeArgs = false,
                includeResult = false)
        public void contextAudited() {
        }
    }
}
