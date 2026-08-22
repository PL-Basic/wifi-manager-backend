package com.plagod.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.dto.device.DeviceNodeCreateDTO;
import com.plagod.dto.device.DeviceNodeUpdateDTO;
import com.plagod.dto.device.KickDeviceDTO;
import com.plagod.dto.device.MacBlacklistCreateDTO;
import com.plagod.dto.device.ManualBlockTrafficDTO;
import com.plagod.dto.device.ManualDisconnectMacDTO;
import com.plagod.dto.device.PortalAuthorizeDTO;
import com.plagod.dto.device.WifiConfigStageDTO;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.entity.device.DeviceWifiConfigRecord;
import com.plagod.entity.device.Esp32Node;
import com.plagod.entity.device.MacBlacklist;
import com.plagod.entity.device.SessionRecord;
import com.plagod.exception.ApiStatusException;
import com.plagod.security.TrustedContextType;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.TrustedRequestContextResolver;
import com.plagod.security.TrustedRequestHeaders;
import com.plagod.security.TrustedSource;
import com.plagod.service.RuleActionExecutor;
import com.plagod.service.impl.DeviceCommandServiceImpl;
import com.plagod.service.impl.DeviceWifiConfigServiceImpl;
import com.plagod.service.impl.MacBlacklistServiceImpl;
import com.plagod.service.impl.ManualDeviceControlServiceImpl;
import com.plagod.service.impl.PortalSessionServiceImpl;
import com.plagod.service.impl.SessionRevokeServiceImpl;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class P15dDeviceAuditTransitionContractTest {

    private static final String CANARY =
            "canary-password-token-cookie-secret";
    private static final String REQUEST_ID = "audit-request-0001";

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void frozenDeviceEntrypointsExposeExactAuditV1Metadata()
            throws Exception {
        List<Entry> entries = entries();
        assertEquals(17, entries.size());

        int auditedMethods = 0;
        for (Class<?> type : ownerTypes()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Audited.class)) {
                    auditedMethods++;
                }
            }
        }
        assertEquals(17, auditedMethods);

        for (Entry entry : entries) {
            Method method = entry.type.getDeclaredMethod(
                    entry.methodName,
                    entry.parameterTypes);
            Audited audited = method.getAnnotation(Audited.class);
            assertEquals(entry.action, audited.action());
            assertEquals(entry.targetType, audited.targetType());
            assertEquals(Audited.Scope.TENANT, audited.scope());
            assertEquals(entry.tenantIdSource,
                    audited.tenantIdSource());
            assertTrue(audited.recordDenied());
            assertTrue(audited.recordFailed());
            assertFalse(audited.includeArgs());
            assertFalse(audited.includeResult());

            Annotation[][] annotations =
                    method.getParameterAnnotations();
            assertMarkedIndex(
                    annotations,
                    AuditTargetId.class,
                    entry.targetIndex);
            assertMarkedIndex(
                    annotations,
                    AuditTenantId.class,
                    entry.tenantIndex);
            assertActorIndex(
                    method.getParameterTypes(),
                    entry.actorIndex);
            assertDetails(annotations, entry.details);
        }
    }

    @Test
    void trustedUserScopeAndSensitiveWifiArgumentsAreSafe()
            throws Throwable {
        RecordingAuditWriter records = new RecordingAuditWriter();
        AuditAspect aspect = aspect(
                records,
                new AuditWriteFailureReporter(null));
        attach(userContext());

        WifiConfigStageDTO request = new WifiConfigStageDTO();
        request.setSsid(CANARY);
        request.setPassword(CANARY);
        Object result = new Object();
        assertEquals(
                result,
                aspect.around(joinPoint(
                        new DeviceWifiConfigServiceImpl(),
                        "stageCandidate",
                        new Class<?>[]{
                                Long.class,
                                String.class,
                                WifiConfigStageDTO.class
                        },
                        new Object[]{11L, "esp32-a", request},
                        result)));

        AuditWriteRecord record = only(records);
        assertEquals(11L, record.getTenantId());
        assertEquals("TENANT", record.getScopeType());
        assertEquals(7L, record.getOperatorId());
        assertEquals("user#7", record.getOperatorName());
        assertEquals("DEVICE:esp32-a", record.getTarget());
        assertTrue(record.getDetail().contains(
                "\"actorType\":\"USER\""));
        assertFalse(record.getDetail().contains(CANARY));
        assertFalse(record.getDetail().contains("\"args\""));
        assertFalse(record.getDetail().contains("\"result\""));
    }

    @Test
    void automaticRuleAcceptsTrustedServiceAndDeviceActors()
            throws Throwable {
        RecordingAuditWriter records = new RecordingAuditWriter();
        AuditAspect aspect = aspect(
                records,
                new AuditWriteFailureReporter(null));

        AuditActorContext serviceActor = AuditActorContext.service(
                TrustedRequestContext.scheduledService("11", REQUEST_ID),
                "device-service",
                "traffic-event-1");
        aspect.around(joinPoint(
                new RuleActionExecutor(),
                "blockTraffic",
                new Class<?>[]{
                        Long.class,
                        String.class,
                        String.class,
                        String.class,
                        Long.class,
                        AuditActorContext.class
                },
                new Object[]{
                        11L,
                        "esp32-a",
                        "1.1.1.1",
                        CANARY,
                        88L,
                        serviceActor
                },
                null));

        AuditActorContext deviceActor = AuditActorContext.device(
                TrustedRequestContext.deviceEvent(
                        "11",
                        "tenant-eleven",
                        REQUEST_ID),
                "esp32-a",
                "traffic-event-2");
        aspect.around(joinPoint(
                new RuleActionExecutor(),
                "disconnectMac",
                new Class<?>[]{
                        Long.class,
                        String.class,
                        String.class,
                        Long.class,
                        AuditActorContext.class
                },
                new Object[]{
                        11L,
                        "esp32-a",
                        CANARY,
                        89L,
                        deviceActor
                },
                null));

        assertEquals(2, records.records.size());
        assertActorRecord(
                records.records.get(0),
                "SERVICE",
                "device-service",
                "traffic-event-1",
                88L);
        assertActorRecord(
                records.records.get(1),
                "DEVICE",
                "esp32-a",
                "traffic-event-2",
                89L);
        assertFalse(records.records.get(0).getDetail().contains(CANARY));
        assertFalse(records.records.get(1).getDetail().contains(CANARY));
    }

    @Test
    void mismatchedExplicitActorTenantDropsAuditMetadata()
            throws Throwable {
        RecordingAuditWriter records = new RecordingAuditWriter();
        AuditWriteFailureReporter reporter =
                new AuditWriteFailureReporter(null);
        AuditAspect aspect = aspect(records, reporter);
        AuditActorContext actor = AuditActorContext.service(
                TrustedRequestContext.scheduledService("12", REQUEST_ID),
                "device-service",
                "traffic-event-3");

        aspect.around(joinPoint(
                new RuleActionExecutor(),
                "disconnectMac",
                new Class<?>[]{
                        Long.class,
                        String.class,
                        String.class,
                        Long.class,
                        AuditActorContext.class
                },
                new Object[]{
                        11L,
                        "esp32-a",
                        "AA:BB:CC:DD:EE:FF",
                        90L,
                        actor
                },
                null));

        assertEquals(0, records.records.size());
        assertEquals(1, reporter.getDroppedEvents());
    }

    @Test
    void deviceEntryRecordsDeniedAndFailedWithFixedErrors()
            throws Throwable {
        RecordingAuditWriter records = new RecordingAuditWriter();
        AuditAspect aspect = aspect(
                records,
                new AuditWriteFailureReporter(null));
        attach(userContext());

        ProceedingJoinPoint denied = joinPoint(
                new DeviceCommandServiceImpl(),
                "restoreDevice",
                new Class<?>[]{Long.class, Long.class},
                new Object[]{11L, 91L},
                null);
        when(denied.proceed()).thenThrow(
                ApiStatusException.forbidden("raw denied " + CANARY));
        assertThrows(
                ApiStatusException.class,
                () -> aspect.around(denied));

        ProceedingJoinPoint failed = joinPoint(
                new DeviceCommandServiceImpl(),
                "restoreDevice",
                new Class<?>[]{Long.class, Long.class},
                new Object[]{11L, 92L},
                null);
        when(failed.proceed()).thenThrow(
                new IllegalStateException("raw failed " + CANARY));
        assertThrows(
                IllegalStateException.class,
                () -> aspect.around(failed));

        assertEquals(2, records.records.size());
        assertTrue(records.records.get(0).getDetail().contains(
                "\"outcome\":\"DENIED\""));
        assertTrue(records.records.get(0).getDetail().contains(
                "\"errorKey\":\"PERMISSION_DENIED\""));
        assertTrue(records.records.get(1).getDetail().contains(
                "\"outcome\":\"FAILED\""));
        assertTrue(records.records.get(1).getDetail().contains(
                "\"errorKey\":\"INTERNAL_ERROR\""));
        assertFalse(records.records.get(0).getDetail().contains(CANARY));
        assertFalse(records.records.get(1).getDetail().contains(CANARY));
    }

    @Test
    void realDeviceCarriersDoNotSupportConditionalTransitionYet() {
        assertTrue(hasField(Esp32Node.class, "status"));
        assertTrue(hasField(Esp32Node.class, "version"));
        assertTrue(hasField(SessionRecord.class, "status"));
        assertTrue(hasField(SessionRecord.class, "version"));
        assertTrue(hasField(DeviceCommandRecord.class, "status"));
        assertTrue(hasField(DeviceCommandRecord.class, "requestId"));
        assertTrue(hasField(DeviceWifiConfigRecord.class, "status"));
        assertTrue(hasField(
                DeviceWifiConfigRecord.class,
                "configVersion"));
        assertTrue(hasField(
                DeviceWifiConfigRecord.class,
                "requestId"));
        assertFalse(hasField(MacBlacklist.class, "status"));

        for (Class<?> carrier : Arrays.asList(
                Esp32Node.class,
                SessionRecord.class,
                DeviceCommandRecord.class,
                DeviceWifiConfigRecord.class,
                MacBlacklist.class)) {
            assertFalse(hasField(carrier, "eventKey"));
        }
    }

    private void assertActorRecord(
            AuditWriteRecord record,
            String actorType,
            String actorId,
            String eventId,
            Long alertId) {
        assertEquals(11L, record.getTenantId());
        assertEquals("TENANT", record.getScopeType());
        assertNull(record.getOperatorId());
        assertEquals("DEVICE:esp32-a", record.getTarget());
        assertTrue(record.getDetail().contains(
                "\"actorType\":\"" + actorType + "\""));
        assertTrue(record.getDetail().contains(
                "\"actorId\":\"" + actorId + "\""));
        assertTrue(record.getDetail().contains(
                "\"eventId\":\"" + eventId + "\""));
        assertTrue(record.getDetail().contains(
                "\"fields\":{\"alertId\":" + alertId + "}"));
    }

    private List<Entry> entries() {
        return Arrays.asList(
                entry(RuleActionExecutor.class, "disconnectMac",
                        "monitor.auto.disconnect-mac", "DEVICE",
                        Audited.TenantIdSource.ARGUMENT,
                        1, 0, 4,
                        types(Long.class, String.class, String.class,
                                Long.class, AuditActorContext.class),
                        detail(3, "alertId")),
                entry(RuleActionExecutor.class, "blockTraffic",
                        "monitor.auto.block-traffic", "DEVICE",
                        Audited.TenantIdSource.ARGUMENT,
                        1, 0, 5,
                        types(Long.class, String.class, String.class,
                                String.class, Long.class,
                                AuditActorContext.class),
                        detail(4, "alertId")),
                entry(SessionRevokeServiceImpl.class, "logout",
                        "session.logout", "SESSION",
                        Audited.TenantIdSource.ARGUMENT,
                        1, 0, -1,
                        types(Long.class, Long.class, Long.class)),
                entry(SessionRevokeServiceImpl.class, "adminRevoke",
                        "session.admin-revoke", "SESSION",
                        Audited.TenantIdSource.ARGUMENT,
                        1, 0, -1,
                        types(Long.class, Long.class, Integer.class)),
                entry(PortalSessionServiceImpl.class, "authorize",
                        "session.portal-authorize", "SESSION",
                        Audited.TenantIdSource.ARGUMENT,
                        -1, 0, -1,
                        types(Long.class, PortalAuthorizeDTO.class,
                                Long.class)),
                entry(MacBlacklistServiceImpl.class, "addBlacklist",
                        "blacklist.add", "BLACKLIST",
                        Audited.TenantIdSource.ARGUMENT,
                        -1, 0, -1,
                        types(Long.class, MacBlacklistCreateDTO.class)),
                entry(DeviceCommandServiceImpl.class, "restoreDevice",
                        "device.restore", "DEVICE",
                        Audited.TenantIdSource.ARGUMENT,
                        1, 0, -1,
                        types(Long.class, Long.class)),
                entry(DeviceCommandServiceImpl.class, "createDevice",
                        "device.create", "DEVICE",
                        Audited.TenantIdSource.ARGUMENT,
                        -1, 0, -1,
                        types(Long.class, DeviceNodeCreateDTO.class)),
                entry(DeviceCommandServiceImpl.class, "updateDevice",
                        "device.update", "DEVICE",
                        Audited.TenantIdSource.ARGUMENT,
                        1, 0, -1,
                        types(Long.class, Long.class,
                                DeviceNodeUpdateDTO.class)),
                entry(DeviceCommandServiceImpl.class, "deleteDevice",
                        "device.delete", "DEVICE",
                        Audited.TenantIdSource.ARGUMENT,
                        1, 0, -1,
                        types(Long.class, Long.class)),
                entry(DeviceCommandServiceImpl.class, "allowDevice",
                        "device.allow", "DEVICE",
                        Audited.TenantIdSource.ARGUMENT,
                        1, 0, -1,
                        types(Long.class, String.class)),
                entry(DeviceCommandServiceImpl.class, "kickDevice",
                        "device.kick", "DEVICE",
                        Audited.TenantIdSource.ARGUMENT,
                        1, 0, -1,
                        types(Long.class, String.class,
                                KickDeviceDTO.class)),
                entry(DeviceCommandServiceImpl.class, "allowClient",
                        "device.allow-client", "SESSION",
                        Audited.TenantIdSource.REQUEST,
                        3, -1, -1,
                        types(Long.class, String.class, String.class,
                                Long.class, Integer.class, Long.class,
                                String.class, String.class),
                        detail(4, "ttlSeconds")),
                entry(DeviceCommandServiceImpl.class, "removeBlacklist",
                        "blacklist.remove", "BLACKLIST",
                        Audited.TenantIdSource.ARGUMENT,
                        1, 0, -1,
                        types(Long.class, String.class)),
                entry(DeviceWifiConfigServiceImpl.class, "stageCandidate",
                        "device.wifi.stage", "DEVICE",
                        Audited.TenantIdSource.ARGUMENT,
                        1, 0, -1,
                        types(Long.class, String.class,
                                WifiConfigStageDTO.class)),
                entry(ManualDeviceControlServiceImpl.class,
                        "disconnectMac",
                        "device.manual-disconnect-mac", "DEVICE",
                        Audited.TenantIdSource.ARGUMENT,
                        1, 0, -1,
                        types(Long.class, String.class,
                                ManualDisconnectMacDTO.class,
                                Integer.class)),
                entry(ManualDeviceControlServiceImpl.class,
                        "blockTraffic",
                        "device.manual-block-traffic", "DEVICE",
                        Audited.TenantIdSource.ARGUMENT,
                        1, 0, -1,
                        types(Long.class, String.class,
                                ManualBlockTrafficDTO.class,
                                Integer.class)));
    }

    private List<Class<?>> ownerTypes() {
        return Arrays.asList(
                RuleActionExecutor.class,
                SessionRevokeServiceImpl.class,
                PortalSessionServiceImpl.class,
                MacBlacklistServiceImpl.class,
                DeviceCommandServiceImpl.class,
                DeviceWifiConfigServiceImpl.class,
                ManualDeviceControlServiceImpl.class);
    }

    private Entry entry(
            Class<?> type,
            String methodName,
            String action,
            String targetType,
            Audited.TenantIdSource tenantIdSource,
            int targetIndex,
            int tenantIndex,
            int actorIndex,
            Class<?>[] parameterTypes,
            Detail... details) {
        Map<Integer, String> detailMap = new LinkedHashMap<>();
        for (Detail detail : details) {
            detailMap.put(detail.index, detail.key);
        }
        return new Entry(
                type,
                methodName,
                action,
                targetType,
                tenantIdSource,
                targetIndex,
                tenantIndex,
                actorIndex,
                parameterTypes,
                detailMap);
    }

    private Class<?>[] types(Class<?>... types) {
        return types;
    }

    private Detail detail(int index, String key) {
        return new Detail(index, key);
    }

    private void assertMarkedIndex(
            Annotation[][] annotations,
            Class<? extends Annotation> annotationType,
            int expectedIndex) {
        int actualIndex = -1;
        for (int index = 0; index < annotations.length; index++) {
            for (Annotation annotation : annotations[index]) {
                if (annotation.annotationType() == annotationType) {
                    assertEquals(-1, actualIndex);
                    actualIndex = index;
                }
            }
        }
        assertEquals(expectedIndex, actualIndex);
    }

    private void assertActorIndex(
            Class<?>[] parameterTypes,
            int expectedIndex) {
        int actualIndex = -1;
        for (int index = 0; index < parameterTypes.length; index++) {
            if (parameterTypes[index] == AuditActorContext.class) {
                assertEquals(-1, actualIndex);
                actualIndex = index;
            }
        }
        assertEquals(expectedIndex, actualIndex);
    }

    private void assertDetails(
            Annotation[][] annotations,
            Map<Integer, String> expected) {
        Map<Integer, String> actual = new LinkedHashMap<>();
        for (int index = 0; index < annotations.length; index++) {
            for (Annotation annotation : annotations[index]) {
                if (annotation instanceof AuditDetail) {
                    actual.put(
                            index,
                            ((AuditDetail) annotation).value());
                }
            }
        }
        assertEquals(expected, actual);
    }

    private boolean hasField(Class<?> type, String fieldName) {
        for (Field field : type.getDeclaredFields()) {
            if (fieldName.equals(field.getName())) {
                return true;
            }
        }
        return false;
    }

    private void attach(TrustedRequestContext context) {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                TrustedRequestHeaders.SOURCE_GATEWAY);
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_CONTEXT_ATTRIBUTE,
                context);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));
    }

    private TrustedRequestContext userContext() {
        return TrustedRequestContext.user(
                TrustedSource.GATEWAY_USER,
                7L,
                1,
                "session-7",
                "token-7",
                TrustedContextType.TENANT,
                "11",
                "tenant-eleven",
                "TENANT_ADMIN",
                2L,
                3L,
                Collections.emptyList(),
                REQUEST_ID);
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
                "device-service");
    }

    private ProceedingJoinPoint joinPoint(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object[] arguments,
            Object result) throws Throwable {
        ProceedingJoinPoint joinPoint =
                mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = target.getClass().getDeclaredMethod(
                methodName,
                parameterTypes);
        when(joinPoint.proceed()).thenReturn(result);
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
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    private static final class Detail {

        private final int index;
        private final String key;

        private Detail(int index, String key) {
            this.index = index;
            this.key = key;
        }
    }

    private static final class Entry {

        private final Class<?> type;
        private final String methodName;
        private final String action;
        private final String targetType;
        private final Audited.TenantIdSource tenantIdSource;
        private final int targetIndex;
        private final int tenantIndex;
        private final int actorIndex;
        private final Class<?>[] parameterTypes;
        private final Map<Integer, String> details;

        private Entry(
                Class<?> type,
                String methodName,
                String action,
                String targetType,
                Audited.TenantIdSource tenantIdSource,
                int targetIndex,
                int tenantIndex,
                int actorIndex,
                Class<?>[] parameterTypes,
                Map<Integer, String> details) {
            this.type = type;
            this.methodName = methodName;
            this.action = action;
            this.targetType = targetType;
            this.tenantIdSource = tenantIdSource;
            this.targetIndex = targetIndex;
            this.tenantIndex = tenantIndex;
            this.actorIndex = actorIndex;
            this.parameterTypes = parameterTypes;
            this.details = details;
        }
    }
}
