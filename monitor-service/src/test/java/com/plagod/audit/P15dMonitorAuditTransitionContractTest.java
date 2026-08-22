package com.plagod.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.dto.ClientLocationReportDTO;
import com.plagod.dto.monitor.AccessRuleCreateDTO;
import com.plagod.dto.monitor.AccessRuleUpdateDTO;
import com.plagod.dto.monitor.GeofenceCreateDTO;
import com.plagod.dto.monitor.GeofenceUpdateDTO;
import com.plagod.entity.monitor.AccessRule;
import com.plagod.entity.monitor.AlertEvent;
import com.plagod.entity.monitor.Geofence;
import com.plagod.entity.monitor.LocationAuthorization;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.AccessRuleMapper;
import com.plagod.mapper.AlertEventMapper;
import com.plagod.mapper.GeofenceMapper;
import com.plagod.security.MonitorTenantScope;
import com.plagod.security.MonitorTrustedRequestContextProvider;
import com.plagod.security.TrustedContextType;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.TrustedRequestContextResolver;
import com.plagod.security.TrustedRequestHeaders;
import com.plagod.security.TrustedSource;
import com.plagod.service.AccessRuleCache;
import com.plagod.service.impl.AccessRuleServiceImpl;
import com.plagod.service.impl.AlertEventServiceImpl;
import com.plagod.service.impl.ClientLocationServiceImpl;
import com.plagod.service.impl.GeofenceAdminServiceImpl;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class P15dMonitorAuditTransitionContractTest {

    private static final String CANARY_TOKEN =
            "Bearer canary-monitor-token";
    private static final String CANARY_CONTENT =
            "canary-monitor-content";

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void frozenMonitorEntrypointsExposeExactAuditMetadata() throws Exception {
        assertAudit(
                ClientLocationServiceImpl.class,
                "report",
                new Class<?>[]{
                        TrustedRequestContext.class,
                        Long.class,
                        ClientLocationReportDTO.class
                },
                "location.report",
                "LOCATION_SESSION",
                "",
                1,
                Collections.emptyMap());
        assertAudit(
                ClientLocationServiceImpl.class,
                "grantAuthorization",
                new Class<?>[]{TrustedRequestContext.class},
                "location.consent.grant",
                "LOCATION_AUTHORIZATION",
                "self",
                -1,
                Collections.emptyMap());
        assertAudit(
                ClientLocationServiceImpl.class,
                "revokeAuthorization",
                new Class<?>[]{TrustedRequestContext.class},
                "location.consent.revoke",
                "LOCATION_AUTHORIZATION",
                "self",
                -1,
                Collections.emptyMap());
        assertAudit(
                ClientLocationServiceImpl.class,
                "clearOwnedHistory",
                new Class<?>[]{TrustedRequestContext.class},
                "location.history.clear",
                "LOCATION_HISTORY",
                "self",
                -1,
                Collections.emptyMap());
        assertAudit(
                AlertEventServiceImpl.class,
                "handle",
                new Class<?>[]{TrustedRequestContext.class, Long.class},
                "alert.handle",
                "ALERT_EVENT",
                "",
                1,
                Collections.emptyMap());
        assertAudit(
                GeofenceAdminServiceImpl.class,
                "create",
                new Class<?>[]{GeofenceCreateDTO.class},
                "geofence.create",
                "GEOFENCE",
                "",
                -1,
                Collections.emptyMap());
        assertAudit(
                GeofenceAdminServiceImpl.class,
                "update",
                new Class<?>[]{Long.class, GeofenceUpdateDTO.class},
                "geofence.update",
                "GEOFENCE",
                "",
                0,
                Collections.emptyMap());
        assertAudit(
                GeofenceAdminServiceImpl.class,
                "toggle",
                new Class<?>[]{Long.class, Integer.class},
                "geofence.toggle",
                "GEOFENCE",
                "",
                0,
                Collections.singletonMap(1, "enabled"));
        assertAudit(
                GeofenceAdminServiceImpl.class,
                "delete",
                new Class<?>[]{Long.class},
                "geofence.delete",
                "GEOFENCE",
                "",
                0,
                Collections.emptyMap());
        assertAudit(
                AccessRuleServiceImpl.class,
                "create",
                new Class<?>[]{AccessRuleCreateDTO.class},
                "rule.create",
                "ACCESS_RULE",
                "",
                -1,
                Collections.emptyMap());
        assertAudit(
                AccessRuleServiceImpl.class,
                "update",
                new Class<?>[]{Long.class, AccessRuleUpdateDTO.class},
                "rule.update",
                "ACCESS_RULE",
                "",
                0,
                Collections.emptyMap());
        assertAudit(
                AccessRuleServiceImpl.class,
                "delete",
                new Class<?>[]{Long.class},
                "rule.delete",
                "ACCESS_RULE",
                "",
                0,
                Collections.emptyMap());
        assertAudit(
                AccessRuleServiceImpl.class,
                "toggleEnabled",
                new Class<?>[]{Long.class, Integer.class},
                "rule.toggle",
                "ACCESS_RULE",
                "",
                0,
                Collections.singletonMap(1, "enabled"));

        int auditedCount = 0;
        for (Class<?> type : monitorTypes()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Audited.class)) {
                    auditedCount++;
                }
            }
        }
        assertEquals(13, auditedCount);
    }

    @Test
    void tenantLocationAuditDoesNotSerializePositionBody() throws Throwable {
        RecordingAuditWriter records = new RecordingAuditWriter();
        ClientLocationReportDTO body = new ClientLocationReportDTO();
        body.setLatitude(new BigDecimal("30.274084"));
        body.setLongitude(new BigDecimal("120.155070"));
        body.setAccuracy(new BigDecimal("8.5"));
        body.setSource(CANARY_CONTENT);

        aspect(records).around(joinPoint(
                new ClientLocationServiceImpl(),
                ClientLocationServiceImpl.class.getDeclaredMethod(
                        "report",
                        TrustedRequestContext.class,
                        Long.class,
                        ClientLocationReportDTO.class),
                new Object[]{tenantContext(), 55L, body},
                301L,
                null));

        AuditWriteRecord record = only(records);
        assertEquals(11L, record.getTenantId());
        assertEquals("TENANT", record.getScopeType());
        assertEquals("LOCATION_SESSION:55", record.getTarget());
        assertFalse(record.getDetail().contains("30.274084"));
        assertFalse(record.getDetail().contains("120.155070"));
        assertFalse(record.getDetail().contains(CANARY_CONTENT));
        assertFalse(record.getDetail().contains("\"args\""));
        assertFalse(record.getDetail().contains("\"result\""));
    }

    @Test
    void platformTenantRuleAuditAllowsOnlyEnabledScalar() throws Throwable {
        RecordingAuditWriter records = new RecordingAuditWriter();
        bindRequest(platformTenantContext());

        aspect(records).around(joinPoint(
                new AccessRuleServiceImpl(),
                AccessRuleServiceImpl.class.getDeclaredMethod(
                        "toggleEnabled",
                        Long.class,
                        Integer.class),
                new Object[]{71L, 1},
                null,
                null));

        AuditWriteRecord record = only(records);
        assertEquals(11L, record.getTenantId());
        assertEquals("TENANT", record.getScopeType());
        assertEquals("ACCESS_RULE:71", record.getTarget());
        assertTrue(record.getDetail().contains(
                "\"contextType\":\"PLATFORM_TENANT\""));
        assertTrue(record.getDetail().contains(
                "\"platformManaged\":true"));
        assertTrue(record.getDetail().contains(
                "\"fields\":{\"enabled\":1}"));

        RecordingAuditWriter sensitiveRecords =
                new RecordingAuditWriter();
        AccessRuleUpdateDTO body = new AccessRuleUpdateDTO();
        body.setPattern(CANARY_TOKEN);
        body.setDescription(CANARY_CONTENT);
        aspect(sensitiveRecords).around(joinPoint(
                new AccessRuleServiceImpl(),
                AccessRuleServiceImpl.class.getDeclaredMethod(
                        "update",
                        Long.class,
                        AccessRuleUpdateDTO.class),
                new Object[]{71L, body},
                null,
                null));
        AuditWriteRecord sensitiveRecord = only(sensitiveRecords);
        assertFalse(sensitiveRecord.getDetail().contains(CANARY_TOKEN));
        assertFalse(sensitiveRecord.getDetail().contains(CANARY_CONTENT));
    }

    @Test
    void deniedAndFailedUseFixedKeysWithoutSensitiveText()
            throws Throwable {
        Method method = AlertEventServiceImpl.class.getDeclaredMethod(
                "handle",
                TrustedRequestContext.class,
                Long.class);

        RecordingAuditWriter deniedRecords = new RecordingAuditWriter();
        ProceedingJoinPoint denied = joinPoint(
                new AlertEventServiceImpl(),
                method,
                new Object[]{platformContext(), 101L},
                null,
                ApiStatusException.forbidden(CANARY_CONTENT));
        assertThrows(
                ApiStatusException.class,
                () -> aspect(deniedRecords).around(denied));
        AuditWriteRecord deniedRecord = only(deniedRecords);
        assertEquals("PLATFORM", deniedRecord.getScopeType());
        assertTrue(deniedRecord.getDetail().contains(
                "\"outcome\":\"DENIED\""));
        assertTrue(deniedRecord.getDetail().contains(
                "\"errorKey\":\"PERMISSION_DENIED\""));
        assertFalse(deniedRecord.getDetail().contains(CANARY_CONTENT));

        RecordingAuditWriter failedRecords = new RecordingAuditWriter();
        ProceedingJoinPoint failed = joinPoint(
                new AlertEventServiceImpl(),
                method,
                new Object[]{tenantContext(), 101L},
                null,
                new IllegalStateException(CANARY_TOKEN));
        assertThrows(
                IllegalStateException.class,
                () -> aspect(failedRecords).around(failed));
        AuditWriteRecord failedRecord = only(failedRecords);
        assertTrue(failedRecord.getDetail().contains(
                "\"outcome\":\"FAILED\""));
        assertTrue(failedRecord.getDetail().contains(
                "\"errorKey\":\"INTERNAL_ERROR\""));
        assertFalse(failedRecord.getDetail().contains(CANARY_TOKEN));
    }

    @Test
    void monitorStateCarriersSupportVersionButNotEventKey()
            throws Exception {
        assertEquals(
                Integer.class,
                LocationAuthorization.class
                        .getDeclaredField("version")
                        .getType());
        assertEquals(
                Integer.class,
                AlertEvent.class.getDeclaredField("version").getType());
        assertEquals(
                Integer.class,
                Geofence.class.getDeclaredField("version").getType());
        assertEquals(
                Integer.class,
                AccessRule.class.getDeclaredField("version").getType());
        assertThrows(
                NoSuchFieldException.class,
                () -> AlertEvent.class.getDeclaredField("eventKey"));
        assertThrows(
                NoSuchFieldException.class,
                () -> Geofence.class.getDeclaredField("eventKey"));
        assertThrows(
                NoSuchFieldException.class,
                () -> AccessRule.class.getDeclaredField("eventKey"));

        assertEquals(
                int.class,
                AlertEventMapper.class.getMethod(
                        "handleByTenantAndVersion",
                        Long.class,
                        Long.class,
                        Long.class,
                        LocalDateTime.class,
                        Integer.class).getReturnType());
        assertEquals(
                int.class,
                GeofenceMapper.class.getMethod(
                        "updateEnabledByTenantAndVersion",
                        Long.class,
                        Long.class,
                        Integer.class,
                        Integer.class).getReturnType());
        assertEquals(
                int.class,
                AccessRuleMapper.class.getMethod(
                        "updateEnabledByTenantAndVersion",
                        Long.class,
                        Long.class,
                        Integer.class,
                        Integer.class).getReturnType());
    }

    @Test
    void accessRuleCacheReloadsOnlyAfterCommit() {
        List<String> events = new ArrayList<>();
        RecordingTransactionManager transactionManager =
                new RecordingTransactionManager(events);
        AccessRuleMapper mapper = mock(AccessRuleMapper.class);
        AccessRuleCache cache = mock(AccessRuleCache.class);
        AccessRuleServiceImpl service = transactionProxy(
                accessRuleTarget(mapper, cache, events),
                transactionManager);
        doAnswer(invocation -> {
            events.add("reload");
            return null;
        }).when(cache).reload();

        service.toggleEnabled(71L, 1);

        assertEquals(
                Arrays.asList("begin", "update", "commit", "reload"),
                events);
    }

    @Test
    void accessRuleCacheDoesNotReloadAfterRollback() {
        List<String> events = new ArrayList<>();
        RecordingTransactionManager transactionManager =
                new RecordingTransactionManager(events);
        AccessRuleMapper mapper = mock(AccessRuleMapper.class);
        AccessRuleCache cache = mock(AccessRuleCache.class);
        AccessRuleServiceImpl service = transactionProxy(
                accessRuleTarget(mapper, cache, events),
                transactionManager);
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.execute(status -> {
            service.toggleEnabled(71L, 1);
            assertFalse(events.contains("reload"));
            status.setRollbackOnly();
            return null;
        });

        assertEquals(
                Arrays.asList("begin", "update", "rollback"),
                events);
        verify(cache, never()).reload();
    }

    @Test
    void accessRuleCacheFailureDoesNotChangeCommittedResult() {
        List<String> events = new ArrayList<>();
        RecordingTransactionManager transactionManager =
                new RecordingTransactionManager(events);
        AccessRuleMapper mapper = mock(AccessRuleMapper.class);
        AccessRuleCache cache = mock(AccessRuleCache.class);
        AccessRuleServiceImpl service = transactionProxy(
                accessRuleTarget(mapper, cache, events),
                transactionManager);
        doAnswer(invocation -> {
            events.add("reload");
            throw new IllegalStateException(CANARY_CONTENT);
        }).when(cache).reload();

        assertDoesNotThrow(() -> service.toggleEnabled(71L, 1));

        assertEquals(
                Arrays.asList("begin", "update", "commit", "reload"),
                events);
    }

    @Test
    void accessRuleCacheReloadsImmediatelyWithoutTransaction() {
        List<String> events = new ArrayList<>();
        AccessRuleMapper mapper = mock(AccessRuleMapper.class);
        AccessRuleCache cache = mock(AccessRuleCache.class);
        AccessRuleServiceImpl service =
                accessRuleTarget(mapper, cache, events);
        doAnswer(invocation -> {
            events.add("reload");
            return null;
        }).when(cache).reload();

        service.toggleEnabled(71L, 1);

        assertEquals(Arrays.asList("update", "reload"), events);
    }

    private void assertAudit(
            Class<?> type,
            String methodName,
            Class<?>[] parameterTypes,
            String action,
            String targetType,
            String target,
            int targetIndex,
            Map<Integer, String> details) throws Exception {
        Method method = type.getDeclaredMethod(methodName, parameterTypes);
        Audited audited = method.getAnnotation(Audited.class);
        assertEquals(action, audited.action());
        assertEquals(targetType, audited.targetType());
        assertEquals(target, audited.target());
        assertEquals(Audited.Scope.CONTEXT, audited.scope());
        assertEquals(
                Audited.TenantIdSource.REQUEST,
                audited.tenantIdSource());
        assertTrue(audited.recordDenied());
        assertTrue(audited.recordFailed());
        assertFalse(audited.includeArgs());
        assertFalse(audited.includeResult());

        Annotation[][] annotations = method.getParameterAnnotations();
        for (int index = 0; index < annotations.length; index++) {
            assertEquals(
                    index == targetIndex,
                    hasAnnotation(annotations[index], AuditTargetId.class));
            AuditDetail detail = findAuditDetail(annotations[index]);
            assertEquals(details.get(index), detail == null
                    ? null
                    : detail.value());
        }
    }

    private boolean hasAnnotation(
            Annotation[] annotations,
            Class<? extends Annotation> type) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType() == type) {
                return true;
            }
        }
        return false;
    }

    private AuditDetail findAuditDetail(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType() == AuditDetail.class) {
                return (AuditDetail) annotation;
            }
        }
        return null;
    }

    private Class<?>[] monitorTypes() {
        return new Class<?>[]{
                ClientLocationServiceImpl.class,
                AlertEventServiceImpl.class,
                GeofenceAdminServiceImpl.class,
                AccessRuleServiceImpl.class
        };
    }

    private AccessRuleServiceImpl accessRuleTarget(
            AccessRuleMapper mapper,
            AccessRuleCache cache,
            List<String> events) {
        AccessRule rule = new AccessRule();
        rule.setId(71L);
        rule.setTenantId(11L);
        rule.setEnabled(0);
        rule.setVersion(3);
        rule.setDelFlag(0);
        when(mapper.selectByIdAndTenantForUpdate(11L, 71L))
                .thenReturn(rule);
        when(mapper.updateEnabledByTenantAndVersion(
                11L,
                71L,
                1,
                3)).thenAnswer(invocation -> {
                    events.add("update");
                    return 1;
                });

        HttpServletRequest request = mock(HttpServletRequest.class);
        MonitorTrustedRequestContextProvider contextProvider =
                mock(MonitorTrustedRequestContextProvider.class);
        when(contextProvider.resolve(request)).thenReturn(tenantContext());

        AccessRuleServiceImpl service = new AccessRuleServiceImpl();
        ReflectionTestUtils.setField(
                service,
                "accessRuleMapper",
                mapper);
        ReflectionTestUtils.setField(
                service,
                "accessRuleCache",
                cache);
        ReflectionTestUtils.setField(
                service,
                "contextProvider",
                contextProvider);
        ReflectionTestUtils.setField(
                service,
                "tenantScope",
                new MonitorTenantScope());
        ReflectionTestUtils.setField(service, "request", request);
        return service;
    }

    @SuppressWarnings("unchecked")
    private <T> T transactionProxy(
            T target,
            RecordingTransactionManager transactionManager) {
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        TransactionInterceptor transactionInterceptor =
                new TransactionInterceptor();
        transactionInterceptor.setTransactionManager(transactionManager);
        transactionInterceptor.setTransactionAttributeSource(
                new AnnotationTransactionAttributeSource());
        proxyFactory.addAdvice(transactionInterceptor);
        return (T) proxyFactory.getProxy();
    }

    private AuditAspect aspect(RecordingAuditWriter records) {
        AuditWriteFailureReporter reporter =
                new AuditWriteFailureReporter(null);
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
                "monitor-service");
    }

    private ProceedingJoinPoint joinPoint(
            Object target,
            Method method,
            Object[] arguments,
            Object result,
            Throwable failure) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
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

    private AuditWriteRecord only(RecordingAuditWriter writer) {
        assertEquals(1, writer.records.size());
        return writer.records.get(0);
    }

    private void bindRequest(TrustedRequestContext context) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("PATCH", "/internal/admin");
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedRequestHeaders.SOURCE_GATEWAY);
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_CONTEXT_ATTRIBUTE,
                context);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));
    }

    private TrustedRequestContext tenantContext() {
        return TrustedRequestContext.user(
                TrustedSource.GATEWAY_USER,
                7L,
                1,
                "session-monitor",
                "token-monitor",
                TrustedContextType.TENANT,
                "11",
                "tenant-a",
                "TENANT_ADMIN",
                1L,
                1L,
                Collections.emptyList(),
                "request-monitor-01");
    }

    private TrustedRequestContext platformTenantContext() {
        return TrustedRequestContext.user(
                TrustedSource.GATEWAY_USER,
                1L,
                0,
                "session-platform-tenant",
                "token-platform-tenant",
                TrustedContextType.PLATFORM_TENANT,
                "11",
                "tenant-a",
                null,
                1L,
                null,
                Collections.singletonList("TENANT_MANAGE"),
                "request-platform-tenant");
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
                "request-platform");
    }

    private static final class RecordingAuditWriter
            implements AuditWriter {

        private final List<AuditWriteRecord> records = new ArrayList<>();

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
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    private static final class RecordingTransactionManager
            extends AbstractPlatformTransactionManager {

        private final ThreadLocal<RecordingTransaction> current =
                new ThreadLocal<>();
        private final List<String> events;

        private RecordingTransactionManager(List<String> events) {
            this.events = events;
        }

        @Override
        protected Object doGetTransaction() {
            RecordingTransaction transaction = current.get();
            return transaction == null
                    ? new RecordingTransaction()
                    : transaction;
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((RecordingTransaction) transaction).active;
        }

        @Override
        protected void doBegin(
                Object transaction,
                TransactionDefinition definition) {
            RecordingTransaction recording =
                    (RecordingTransaction) transaction;
            recording.active = true;
            current.set(recording);
            events.add("begin");
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            events.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            events.add("rollback");
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            ((RecordingTransaction) transaction).active = false;
            current.remove();
        }
    }

    private static final class RecordingTransaction {

        private boolean active;
    }
}
