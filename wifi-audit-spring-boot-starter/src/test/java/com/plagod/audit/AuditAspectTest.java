package com.plagod.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.exception.ApiStatusException;
import com.plagod.security.TrustedContextType;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.TrustedRequestContextResolver;
import com.plagod.security.TrustedRequestHeaders;
import com.plagod.security.TrustedSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditAspectTest {

    private static final String CANARY_PASSWORD =
            "canary-password-secret";
    private static final String REQUEST_ID = "audit-request-42";

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void safeDefaultsNeverSerializeArgsOrResult() throws Exception {
        Audited audited = AuditFixture.class
                .getDeclaredMethod(
                        "legacyDefaults",
                        TrustedRequestContext.class,
                        String.class)
                .getAnnotation(Audited.class);

        assertFalse(audited.includeArgs());
        assertFalse(audited.includeResult());
        assertFalse(audited.recordDenied());
        assertFalse(audited.recordFailed());
    }

    @Test
    void successUsesTrustedActorExplicitTargetAndAllowlist()
            throws Throwable {
        RecordingAuditWriter records = new RecordingAuditWriter();
        AuditAspect aspect = aspect(records);
        TrustedRequestContext context = tenantContext();

        assertEquals(
                "done",
                aspect.around(joinPoint(
                        "tenantAction",
                        new Class<?>[]{
                                TrustedRequestContext.class,
                                Long.class,
                                String.class,
                                String.class
                        },
                        new Object[]{
                                context,
                                7L,
                                "ACTIVE",
                                CANARY_PASSWORD
                        },
                        "done")));

        AuditWriteRecord record = only(records);
        assertEquals(9L, record.getTenantId());
        assertEquals("TENANT", record.getScopeType());
        assertEquals(42L, record.getOperatorId());
        assertEquals("user#42", record.getOperatorName());
        assertEquals("DEVICE:7", record.getTarget());
        assertTrue(record.getDetail().contains("\"outcome\":\"SUCCESS\""));
        assertTrue(record.getDetail().contains(
                "\"fields\":{\"status\":\"ACTIVE\"}"));
        assertFalse(record.getDetail().contains(CANARY_PASSWORD));
        assertFalse(record.getDetail().contains("\"args\""));
        assertFalse(record.getDetail().contains("\"result\""));
    }

    @Test
    void deniedUsesFixedErrorKeyAndIndependentWriter()
            throws Throwable {
        RecordingAuditWriter records = new RecordingAuditWriter();
        AuditAspect aspect = aspect(records);
        ProceedingJoinPoint joinPoint = joinPoint(
                "tenantAction",
                new Class<?>[]{
                        TrustedRequestContext.class,
                        Long.class,
                        String.class,
                        String.class
                },
                new Object[]{
                        tenantContext(),
                        7L,
                        "ACTIVE",
                        CANARY_PASSWORD
                },
                null);
        when(joinPoint.proceed()).thenThrow(
                ApiStatusException.forbidden("raw denied text"));

        assertThrows(
                ApiStatusException.class,
                () -> aspect.around(joinPoint));

        AuditWriteRecord record = only(records);
        assertTrue(record.getDetail().contains(
                "\"outcome\":\"DENIED\""));
        assertTrue(record.getDetail().contains(
                "\"errorKey\":\"PERMISSION_DENIED\""));
        assertFalse(record.getDetail().contains("raw denied text"));
        assertFalse(record.getDetail().contains(CANARY_PASSWORD));
    }

    @Test
    void unknownFailureUsesInternalErrorWithoutExceptionText()
            throws Throwable {
        RecordingAuditWriter records = new RecordingAuditWriter();
        AuditAspect aspect = aspect(records);
        ProceedingJoinPoint joinPoint = joinPoint(
                "tenantAction",
                new Class<?>[]{
                        TrustedRequestContext.class,
                        Long.class,
                        String.class,
                        String.class
                },
                new Object[]{
                        tenantContext(),
                        7L,
                        "ACTIVE",
                        CANARY_PASSWORD
                },
                null);
        when(joinPoint.proceed()).thenThrow(
                new IllegalStateException("database password leaked"));

        assertThrows(
                IllegalStateException.class,
                () -> aspect.around(joinPoint));

        AuditWriteRecord record = only(records);
        assertTrue(record.getDetail().contains(
                "\"outcome\":\"FAILED\""));
        assertTrue(record.getDetail().contains(
                "\"errorKey\":\"INTERNAL_ERROR\""));
        assertFalse(record.getDetail().contains("database password"));
    }

    @Test
    void platformTenantUsesTenantScopeAndMarksManagement()
            throws Throwable {
        RecordingAuditWriter records = new RecordingAuditWriter();
        AuditAspect aspect = aspect(records);

        aspect.around(joinPoint(
                "contextAction",
                new Class<?>[]{TrustedRequestContext.class},
                new Object[]{platformTenantContext()},
                "done"));

        AuditWriteRecord record = only(records);
        assertEquals(9L, record.getTenantId());
        assertEquals("TENANT", record.getScopeType());
        assertTrue(record.getDetail().contains(
                "\"contextType\":\"PLATFORM_TENANT\""));
        assertTrue(record.getDetail().contains(
                "\"platformManaged\":true"));
    }

    @Test
    void anonymousActorRequiresTrustedGatewaySource()
            throws Throwable {
        RecordingAuditWriter records = new RecordingAuditWriter();
        AuditAspect aspect = aspect(records);
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/register");
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedRequestHeaders.SOURCE_GATEWAY);
        request.addHeader("X-Request-Id", REQUEST_ID);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));

        aspect.around(joinPoint(
                "anonymousPlatformAction",
                new Class<?>[]{String.class},
                new Object[]{CANARY_PASSWORD},
                "done"));

        AuditWriteRecord record = only(records);
        assertEquals("PLATFORM", record.getScopeType());
        assertEquals("anonymous", record.getOperatorName());
        assertTrue(record.getDetail().contains(
                "\"actorType\":\"ANONYMOUS\""));
        assertFalse(record.getDetail().contains(CANARY_PASSWORD));
    }

    @Test
    void noTargetMarkerDoesNotGuessScalarArgument()
            throws Throwable {
        RecordingAuditWriter records = new RecordingAuditWriter();
        AuditAspect aspect = aspect(records);

        aspect.around(joinPoint(
                "legacyDefaults",
                new Class<?>[]{
                        TrustedRequestContext.class,
                        String.class
                },
                new Object[]{tenantContext(), "must-not-be-target"},
                "done"));

        assertEquals("LEGACY", only(records).getTarget());
    }

    @Test
    void futureResultIsReturnedAndDroppedWithoutSuccess()
            throws Throwable {
        RecordingAuditWriter records = new RecordingAuditWriter();
        SimpleMeterRegistry meterRegistry =
                new SimpleMeterRegistry();
        AuditWriteFailureReporter reporter =
                new AuditWriteFailureReporter(meterRegistry);
        AuditAspect aspect = aspect(records, reporter);
        FutureTask<String> future =
                new FutureTask<>(() -> "done");

        Object result = aspect.around(joinPoint(
                "legacyDefaults",
                new Class<?>[]{
                        TrustedRequestContext.class,
                        String.class
                },
                new Object[]{tenantContext(), "ignored"},
                future));

        assertSame(future, result);
        assertEquals(0, records.records.size());
        assertEquals(1, reporter.getDroppedEvents());
        assertEquals(
                1.0,
                meterRegistry.counter(
                        "wifi.audit.events.dropped",
                        "reason",
                        "unsupported_async_result").count());
    }

    @Test
    void completionStageIsReturnedAndDroppedWithoutSuccess()
            throws Throwable {
        RecordingAuditWriter records = new RecordingAuditWriter();
        AuditWriteFailureReporter reporter =
                new AuditWriteFailureReporter(null);
        AuditAspect aspect = aspect(records, reporter);
        CompletionStage<String> stage =
                CompletableFuture.completedFuture("done");

        Object result = aspect.around(joinPoint(
                "legacyDefaults",
                new Class<?>[]{
                        TrustedRequestContext.class,
                        String.class
                },
                new Object[]{tenantContext(), "ignored"},
                stage));

        assertSame(stage, result);
        assertEquals(0, records.records.size());
        assertEquals(1, reporter.getDroppedEvents());
    }

    private AuditAspect aspect(RecordingAuditWriter records) {
        AuditWriteFailureReporter reporter =
                new AuditWriteFailureReporter(null);
        return aspect(records, reporter);
    }

    private AuditAspect aspect(
            RecordingAuditWriter records,
            AuditWriteFailureReporter reporter) {
        TestTransactionManager transactionManager =
                new TestTransactionManager();
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
                "test-service");
    }

    private AuditWriteRecord only(RecordingAuditWriter writer) {
        assertEquals(1, writer.records.size());
        return writer.records.get(0);
    }

    private TrustedRequestContext tenantContext() {
        return TrustedRequestContext.user(
                TrustedSource.GATEWAY_USER,
                42L,
                1,
                "session-42",
                "token-42",
                TrustedContextType.TENANT,
                "9",
                "tenant-nine",
                "TENANT_ADMIN",
                2L,
                3L,
                Collections.emptyList(),
                REQUEST_ID);
    }

    private TrustedRequestContext platformTenantContext() {
        return TrustedRequestContext.user(
                TrustedSource.GATEWAY_USER,
                1L,
                0,
                "session-1",
                "token-1",
                TrustedContextType.PLATFORM_TENANT,
                "9",
                "tenant-nine",
                null,
                2L,
                null,
                Collections.singletonList("TENANT_MANAGE"),
                REQUEST_ID);
    }

    private ProceedingJoinPoint joinPoint(
            String methodName,
            Class<?>[] parameterTypes,
            Object[] arguments,
            Object result) throws Throwable {
        ProceedingJoinPoint joinPoint =
                mock(ProceedingJoinPoint.class);
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

    private static final class RecordingAuditWriter
            implements AuditWriter {

        private final List<AuditWriteRecord> records =
                new ArrayList<>();

        @Override
        public void append(AuditWriteRecord record) {
            records.add(record);
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

    private static class AuditFixture {

        @Audited(
                action = "device.update",
                targetType = "DEVICE",
                scope = Audited.Scope.TENANT,
                tenantIdSource = Audited.TenantIdSource.REQUEST,
                recordDenied = true,
                recordFailed = true)
        public void tenantAction(
                TrustedRequestContext context,
                @AuditTargetId Long deviceId,
                @AuditDetail("status") String status,
                String password) {
        }

        @Audited(
                action = "tenant.manage",
                targetType = "TENANT",
                scope = Audited.Scope.CONTEXT,
                tenantIdSource = Audited.TenantIdSource.REQUEST)
        public void contextAction(
                TrustedRequestContext context) {
        }

        @Audited(
                action = "auth.register",
                targetType = "ACCOUNT",
                scope = Audited.Scope.PLATFORM,
                tenantIdSource = Audited.TenantIdSource.REQUEST)
        public void anonymousPlatformAction(String password) {
        }

        @Audited(
                action = "legacy.action",
                targetType = "LEGACY",
                scope = Audited.Scope.TENANT,
                tenantIdSource = Audited.TenantIdSource.REQUEST)
        public void legacyDefaults(
                TrustedRequestContext context,
                String scalar) {
        }
    }
}
