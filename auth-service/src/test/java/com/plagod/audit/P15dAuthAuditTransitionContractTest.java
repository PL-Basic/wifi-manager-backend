package com.plagod.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.client.TenantContextClient;
import com.plagod.controller.TenantContextController;
import com.plagod.dto.RegisterDTO;
import com.plagod.dto.ResetPasswordDTO;
import com.plagod.dto.tenant.PlatformTenantContextRequest;
import com.plagod.exception.ApiStatusException;
import com.plagod.security.TrustedContextType;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.TrustedRequestContextResolver;
import com.plagod.security.TrustedRequestHeaders;
import com.plagod.security.TrustedSource;
import com.plagod.service.AuthSessionService;
import com.plagod.service.impl.UserServiceImpl;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class P15dAuthAuditTransitionContractTest {

    private static final String CANARY_PASSWORD =
            "canary-password-secret";
    private static final String CANARY_TOKEN =
            "canary-token-secret";
    private static final String CANARY_CONTENT =
            "canary-content-secret";
    private static final String REQUEST_ID =
            "p15d-auth-request-42";

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void exposesOnlyTheFourFrozenAuthAuditEntries()
            throws Exception {
        Set<String> auditedMethods = new HashSet<>();
        collectAuditedMethods(
                TenantContextController.class,
                auditedMethods);
        collectAuditedMethods(
                UserServiceImpl.class,
                auditedMethods);

        assertEquals(
                new HashSet<>(Arrays.asList(
                        "TenantContextController#returnPlatform",
                        "TenantContextController#enterPlatformTenant",
                        "UserServiceImpl#register",
                        "UserServiceImpl#resetPassword")),
                auditedMethods);

        Method returnPlatform = TenantContextController.class
                .getDeclaredMethod(
                        "returnPlatform",
                        String.class,
                        Long.class,
                        Integer.class);
        Audited returnAudit =
                returnPlatform.getAnnotation(Audited.class);
        assertEquals("CONTEXT", returnAudit.targetType());
        assertEquals(
                Audited.Scope.CONTEXT,
                returnAudit.scope());
        assertEquals("PLATFORM", returnAudit.target());
        assertTrue(returnAudit.recordDenied());
        assertTrue(returnAudit.recordFailed());
        assertEquals(
                "globalRole",
                auditDetailKey(returnPlatform, 2));
        assertFalse(hasParameterAnnotation(
                returnPlatform,
                AuditTargetId.class));

        Method enterPlatformTenant =
                TenantContextController.class.getDeclaredMethod(
                        "enterPlatformTenant",
                        String.class,
                        PlatformTenantContextRequest.class,
                        String.class,
                        Long.class,
                        Integer.class);
        Audited enterAudit =
                enterPlatformTenant.getAnnotation(Audited.class);
        assertEquals("TENANT", enterAudit.targetType());
        assertEquals(
                Audited.Scope.CONTEXT,
                enterAudit.scope());
        assertTrue(enterAudit.recordDenied());
        assertTrue(enterAudit.recordFailed());
        assertTrue(hasParameterAnnotation(
                enterPlatformTenant,
                0,
                AuditTargetId.class));
        assertEquals(
                "globalRole",
                auditDetailKey(enterPlatformTenant, 4));
        assertNull(auditDetailKey(enterPlatformTenant, 1));
        assertNull(auditDetailKey(enterPlatformTenant, 2));

        Method register = UserServiceImpl.class
                .getDeclaredMethod(
                        "register",
                        RegisterDTO.class,
                        String.class,
                        String.class);
        assertAnonymousAccountAudit(register);

        Method resetPassword = UserServiceImpl.class
                .getDeclaredMethod(
                        "resetPassword",
                        ResetPasswordDTO.class,
                        String.class);
        assertAnonymousAccountAudit(resetPassword);
    }

    @Test
    void trustedUserContextsProducePlatformAndManagedTenantScopes()
            throws Throwable {
        RecordingAuditWriter records =
                new RecordingAuditWriter();
        AuditAspect aspect = aspect(records);
        TenantContextController controller =
                tenantContextController();
        PlatformTenantContextRequest request =
                new PlatformTenantContextRequest();
        request.setReason(CANARY_CONTENT);

        bindTrustedRequest(platformContext());
        Method enter = TenantContextController.class
                .getDeclaredMethod(
                        "enterPlatformTenant",
                        String.class,
                        PlatformTenantContextRequest.class,
                        String.class,
                        Long.class,
                        Integer.class);
        aspect.around(joinPoint(
                controller,
                enter,
                new Object[]{
                        "9",
                        request,
                        CANARY_TOKEN,
                        1L,
                        0
                },
                null,
                null));

        AuditWriteRecord platformRecord = only(records);
        assertEquals("PLATFORM", platformRecord.getScopeType());
        assertEquals("TENANT:9", platformRecord.getTarget());
        assertTrue(platformRecord.getDetail().contains(
                "\"contextType\":\"PLATFORM\""));
        assertTrue(platformRecord.getDetail().contains(
                "\"fields\":{\"globalRole\":0}"));
        assertNoSensitiveDetail(platformRecord);

        records.clear();
        bindTrustedRequest(platformTenantContext());
        Method returnPlatform = TenantContextController.class
                .getDeclaredMethod(
                        "returnPlatform",
                        String.class,
                        Long.class,
                        Integer.class);
        aspect.around(joinPoint(
                controller,
                returnPlatform,
                new Object[]{CANARY_TOKEN, 1L, 0},
                null,
                null));

        AuditWriteRecord managedRecord = only(records);
        assertEquals(9L, managedRecord.getTenantId());
        assertEquals("TENANT", managedRecord.getScopeType());
        assertEquals(
                "CONTEXT:PLATFORM",
                managedRecord.getTarget());
        assertTrue(managedRecord.getDetail().contains(
                "\"contextType\":\"PLATFORM_TENANT\""));
        assertTrue(managedRecord.getDetail().contains(
                "\"platformManaged\":true"));
        assertNoSensitiveDetail(managedRecord);
    }

    @Test
    void registerAcceptsOnlyTrustedGatewayAnonymousActor()
            throws Throwable {
        RecordingAuditWriter records =
                new RecordingAuditWriter();
        AuditAspect aspect = aspect(records);
        bindAnonymousGatewayRequest();

        RegisterDTO request = new RegisterDTO();
        request.setUsername(CANARY_CONTENT);
        request.setPassword(CANARY_PASSWORD);
        request.setNickname(CANARY_TOKEN);
        Method register = UserServiceImpl.class
                .getDeclaredMethod(
                        "register",
                        RegisterDTO.class,
                        String.class,
                        String.class);
        aspect.around(joinPoint(
                new UserServiceImpl(),
                register,
                new Object[]{
                        request,
                        CANARY_TOKEN,
                        CANARY_CONTENT
                },
                null,
                null));

        AuditWriteRecord record = only(records);
        assertEquals("PLATFORM", record.getScopeType());
        assertEquals("anonymous", record.getOperatorName());
        assertEquals("ACCOUNT", record.getTarget());
        assertTrue(record.getDetail().contains(
                "\"actorType\":\"ANONYMOUS\""));
        assertTrue(record.getDetail().contains(
                "\"trustedSource\":\"GATEWAY\""));
        assertNoSensitiveDetail(record);
    }

    @Test
    void deniedAndFailedOutcomesUseFixedKeysWithoutSecrets()
            throws Throwable {
        RecordingAuditWriter records =
                new RecordingAuditWriter();
        AuditAspect aspect = aspect(records);
        TenantContextController controller =
                tenantContextController();
        bindTrustedRequest(platformContext());

        PlatformTenantContextRequest request =
                new PlatformTenantContextRequest();
        request.setReason(CANARY_CONTENT);
        Method enter = TenantContextController.class
                .getDeclaredMethod(
                        "enterPlatformTenant",
                        String.class,
                        PlatformTenantContextRequest.class,
                        String.class,
                        Long.class,
                        Integer.class);
        ApiStatusException denied =
                ApiStatusException.forbidden(CANARY_CONTENT);
        assertThrows(
                ApiStatusException.class,
                () -> aspect.around(joinPoint(
                        controller,
                        enter,
                        new Object[]{
                                "9",
                                request,
                                CANARY_TOKEN,
                                1L,
                                0
                        },
                        null,
                        denied)));

        AuditWriteRecord deniedRecord = only(records);
        assertTrue(deniedRecord.getDetail().contains(
                "\"outcome\":\"DENIED\""));
        assertTrue(deniedRecord.getDetail().contains(
                "\"errorKey\":\"PERMISSION_DENIED\""));
        assertNoSensitiveDetail(deniedRecord);

        records.clear();
        bindAnonymousGatewayRequest();
        ResetPasswordDTO resetRequest =
                new ResetPasswordDTO();
        resetRequest.setTarget(CANARY_CONTENT);
        resetRequest.setCode(CANARY_TOKEN);
        resetRequest.setNewPassword(CANARY_PASSWORD);
        Method resetPassword = UserServiceImpl.class
                .getDeclaredMethod(
                        "resetPassword",
                        ResetPasswordDTO.class,
                        String.class);
        IllegalStateException failed =
                new IllegalStateException(CANARY_PASSWORD);
        assertThrows(
                IllegalStateException.class,
                () -> aspect.around(joinPoint(
                        new UserServiceImpl(),
                        resetPassword,
                        new Object[]{
                                resetRequest,
                                CANARY_CONTENT
                        },
                        null,
                        failed)));

        AuditWriteRecord failedRecord = only(records);
        assertTrue(failedRecord.getDetail().contains(
                "\"outcome\":\"FAILED\""));
        assertTrue(failedRecord.getDetail().contains(
                "\"errorKey\":\"INTERNAL_ERROR\""));
        assertNoSensitiveDetail(failedRecord);
    }

    private void collectAuditedMethods(
            Class<?> type,
            Set<String> auditedMethods) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getAnnotation(Audited.class) != null) {
                auditedMethods.add(
                        type.getSimpleName()
                                + "#"
                                + method.getName());
            }
        }
    }

    private void assertAnonymousAccountAudit(Method method) {
        Audited audited = method.getAnnotation(Audited.class);
        assertEquals("ACCOUNT", audited.targetType());
        assertEquals(Audited.Scope.PLATFORM, audited.scope());
        assertFalse(audited.recordDenied());
        assertTrue(audited.recordFailed());
        assertFalse(audited.includeArgs());
        assertFalse(audited.includeResult());
        assertFalse(hasParameterAnnotation(
                method,
                AuditTargetId.class));
        assertFalse(hasParameterAnnotation(
                method,
                AuditDetail.class));
    }

    private boolean hasParameterAnnotation(
            Method method,
            Class<? extends Annotation> annotationType) {
        for (int index = 0;
             index < method.getParameterCount();
             index++) {
            if (hasParameterAnnotation(
                    method,
                    index,
                    annotationType)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasParameterAnnotation(
            Method method,
            int parameterIndex,
            Class<? extends Annotation> annotationType) {
        for (Annotation annotation :
                method.getParameterAnnotations()[parameterIndex]) {
            if (annotation.annotationType() == annotationType) {
                return true;
            }
        }
        return false;
    }

    private String auditDetailKey(
            Method method,
            int parameterIndex) {
        for (Annotation annotation :
                method.getParameterAnnotations()[parameterIndex]) {
            if (annotation.annotationType() == AuditDetail.class) {
                return ((AuditDetail) annotation).value();
            }
        }
        return null;
    }

    private TenantContextController tenantContextController() {
        return new TenantContextController(
                mock(TenantContextClient.class),
                mock(AuthSessionService.class),
                transactionManager(),
                "internal-token");
    }

    private AuditAspect aspect(
            RecordingAuditWriter records) {
        AuditWriteFailureReporter reporter =
                new AuditWriteFailureReporter(null);
        TestTransactionManager transactionManager =
                transactionManager();
        return new AuditAspect(
                new AfterCommitAuditWriter(
                        records,
                        transactionManager,
                        reporter),
                new IndependentAuditWriter(
                        records,
                        transactionManager,
                        reporter),
                reporter,
                new TrustedRequestContextResolver(),
                new ObjectMapper(),
                "auth-service");
    }

    private TestTransactionManager transactionManager() {
        return new TestTransactionManager();
    }

    private ProceedingJoinPoint joinPoint(
            Object target,
            Method method,
            Object[] arguments,
            Object result,
            Throwable failure) throws Throwable {
        ProceedingJoinPoint joinPoint =
                mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        if (failure == null) {
            when(joinPoint.proceed()).thenReturn(result);
        } else {
            when(joinPoint.proceed()).thenThrow(failure);
        }
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getArgs()).thenReturn(arguments);
        when(signature.getMethod()).thenReturn(method);
        return joinPoint;
    }

    private void bindTrustedRequest(
            TrustedRequestContext context) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/auth/context");
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedRequestHeaders.SOURCE_GATEWAY);
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_CONTEXT_ATTRIBUTE,
                context);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));
    }

    private void bindAnonymousGatewayRequest() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/auth/register");
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedRequestHeaders.SOURCE_GATEWAY);
        request.addHeader("X-Request-Id", REQUEST_ID);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));
    }

    private TrustedRequestContext platformContext() {
        return TrustedRequestContext.user(
                TrustedSource.GATEWAY_USER,
                1L,
                0,
                "session-platform",
                "token-platform",
                TrustedContextType.PLATFORM,
                null,
                null,
                null,
                null,
                null,
                Collections.singletonList("TENANT_MANAGE"),
                REQUEST_ID);
    }

    private TrustedRequestContext platformTenantContext() {
        return TrustedRequestContext.user(
                TrustedSource.GATEWAY_USER,
                1L,
                0,
                "session-platform-tenant",
                "token-platform-tenant",
                TrustedContextType.PLATFORM_TENANT,
                "9",
                "tenant-nine",
                null,
                2L,
                null,
                Collections.singletonList("TENANT_MANAGE"),
                REQUEST_ID);
    }

    private AuditWriteRecord only(
            RecordingAuditWriter records) {
        assertEquals(1, records.records.size());
        return records.records.get(0);
    }

    private void assertNoSensitiveDetail(
            AuditWriteRecord record) {
        assertFalse(record.getDetail().contains(CANARY_PASSWORD));
        assertFalse(record.getDetail().contains(CANARY_TOKEN));
        assertFalse(record.getDetail().contains(CANARY_CONTENT));
        assertFalse(record.getDetail().contains("\"args\""));
        assertFalse(record.getDetail().contains("\"result\""));
    }

    private static final class RecordingAuditWriter
            implements AuditWriter {

        private final List<AuditWriteRecord> records =
                new ArrayList<>();

        @Override
        public void append(AuditWriteRecord record) {
            records.add(record);
        }

        private void clear() {
            records.clear();
        }
    }

    private static final class TestTransactionManager
            extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(
                Object transaction,
                TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(
                DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(
                DefaultTransactionStatus status) {
        }
    }
}
