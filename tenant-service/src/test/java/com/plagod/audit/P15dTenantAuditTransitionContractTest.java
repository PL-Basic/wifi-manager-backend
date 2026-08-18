package com.plagod.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.client.UserRoleClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.TenantCreateRequest;
import com.plagod.dto.tenant.TenantStatusRequest;
import com.plagod.dto.tenant.TenantUpdateRequest;
import com.plagod.entity.Tenant;
import com.plagod.entity.TenantMember;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.SaasPlanMapper;
import com.plagod.mapper.TenantCreationReceiptMapper;
import com.plagod.mapper.TenantDomainOutboxMapper;
import com.plagod.mapper.TenantMapper;
import com.plagod.mapper.TenantMemberMapper;
import com.plagod.mapper.TenantSubscriptionMapper;
import com.plagod.security.TrustedContextType;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.TrustedRequestContextResolver;
import com.plagod.security.TrustedSource;
import com.plagod.service.impl.TenantServiceImpl;
import com.plagod.vo.tenant.TenantVO;
import com.plagod.vo.user.UserRoleSnapshotVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class P15dTenantAuditTransitionContractTest {

    private static final String CANARY_SECRET =
            "p15d-canary-secret-must-not-appear";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<AuditWriteRecord> records = new ArrayList<>();

    private TenantMapper tenantMapper;
    private TenantMemberMapper tenantMemberMapper;
    private TenantCreationReceiptMapper creationReceiptMapper;
    private TenantDomainOutboxMapper tenantDomainOutboxMapper;
    private TenantSubscriptionMapper tenantSubscriptionMapper;
    private UserRoleClient userRoleClient;
    private TenantServiceImpl service;

    @BeforeEach
    void setUp() {
        tenantMapper = mock(TenantMapper.class);
        tenantMemberMapper = mock(TenantMemberMapper.class);
        creationReceiptMapper =
                mock(TenantCreationReceiptMapper.class);
        tenantDomainOutboxMapper =
                mock(TenantDomainOutboxMapper.class);
        tenantSubscriptionMapper =
                mock(TenantSubscriptionMapper.class);
        userRoleClient = mock(UserRoleClient.class);

        TestTransactionManager transactionManager =
                new TestTransactionManager();
        TenantServiceImpl target = new TenantServiceImpl(
                tenantMapper,
                tenantMemberMapper,
                mock(SaasPlanMapper.class),
                tenantSubscriptionMapper,
                creationReceiptMapper,
                tenantDomainOutboxMapper,
                userRoleClient,
                transactionManager);

        AuditWriter writer = records::add;
        AuditWriteFailureReporter reporter =
                new AuditWriteFailureReporter(null);
        AuditAspect aspect = new AuditAspect(
                new AfterCommitAuditWriter(
                        writer,
                        transactionManager,
                        reporter),
                new IndependentAuditWriter(
                        writer,
                        transactionManager,
                        reporter),
                reporter,
                mock(TrustedRequestContextResolver.class),
                objectMapper,
                "tenant-service");

        AspectJProxyFactory proxyFactory =
                new AspectJProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(aspect);
        service = proxyFactory.getProxy();
    }

    @Test
    void threeFrozenEntrypointsExposeExactAuditMetadata()
            throws Exception {
        Method create = TenantServiceImpl.class.getMethod(
                "createTenant",
                TenantCreateRequest.class,
                TrustedRequestContext.class);
        Method update = TenantServiceImpl.class.getMethod(
                "updateTenant",
                TrustedRequestContext.class,
                String.class,
                TenantUpdateRequest.class);
        Method status = TenantServiceImpl.class.getMethod(
                "updateStatus",
                TrustedRequestContext.class,
                String.class,
                TenantStatusRequest.class);

        assertAudited(
                create,
                "tenant.create",
                Audited.Scope.PLATFORM);
        assertAudited(
                update,
                "tenant.update",
                Audited.Scope.CONTEXT);
        assertAudited(
                status,
                "tenant.status",
                Audited.Scope.CONTEXT);
        assertFalse(hasAnnotation(
                create.getParameterAnnotations(),
                AuditTargetId.class));
        assertTargetAndDetailAllowlist(update);
        assertTargetAndDetailAllowlist(status);
    }

    @Test
    void createWritesPlatformScopeWithoutSerializingRequest()
            throws Exception {
        when(userRoleClient.getRoleSnapshots(any()))
                .thenReturn(ApiResponse.success(
                        Collections.singletonList(
                                activePlatformUser())));
        when(tenantMapper.insert(any(Tenant.class)))
                .thenAnswer(invocation -> {
                    Tenant tenant = invocation.getArgument(0);
                    tenant.setTenantId(41L);
                    return 1;
                });
        when(tenantMemberMapper.insert(any(TenantMember.class)))
                .thenReturn(1);
        when(creationReceiptMapper.insert(any())).thenReturn(1);
        when(tenantDomainOutboxMapper.insert(any())).thenReturn(1);
        when(tenantMemberMapper.selectCount(any())).thenReturn(1L);

        TenantVO result = service.createTenant(
                createRequest(CANARY_SECRET),
                platformContext());

        assertEquals("41", result.getTenantId());
        AuditWriteRecord record = onlyRecord();
        assertEquals("tenant.create", record.getAction());
        assertEquals("TENANT", record.getTarget());
        assertEquals("PLATFORM", record.getScopeType());
        assertNull(record.getTenantId());
        JsonNode detail = objectMapper.readTree(record.getDetail());
        assertEquals("PLATFORM",
                detail.path("contextType").asText());
        assertFalse(detail.has("fields"));
        assertFalse(record.getDetail().contains(CANARY_SECRET));
    }

    @Test
    void tenantUpdateWritesTenantScopeAndOnlyAllowlistedScalar()
            throws Exception {
        when(tenantMapper.selectOne(any()))
                .thenReturn(tenant(9L, "ACTIVE"));
        when(tenantMapper.update(any(), any())).thenReturn(1);
        when(tenantMemberMapper.selectCount(any())).thenReturn(1L);
        TenantUpdateRequest request = new TenantUpdateRequest();
        request.setName(CANARY_SECRET);
        request.setTimezone("Asia/Shanghai");

        service.updateTenant(
                tenantContext("9"),
                "9",
                request);

        AuditWriteRecord record = onlyRecord();
        assertEquals("tenant.update", record.getAction());
        assertEquals("TENANT:9", record.getTarget());
        assertEquals("TENANT", record.getScopeType());
        assertEquals(9L, record.getTenantId());
        JsonNode detail = objectMapper.readTree(record.getDetail());
        assertEquals("TENANT",
                detail.path("contextType").asText());
        assertFalse(detail.path("platformManaged").asBoolean());
        assertEquals("9",
                detail.path("fields").path("tenantId").asText());
        assertFalse(record.getDetail().contains(CANARY_SECRET));
    }

    @Test
    void platformTenantStatusWritesManagedTenantScope()
            throws Exception {
        Tenant active = tenant(9L, "ACTIVE");
        Tenant disabled = tenant(9L, "DISABLED");
        disabled.setVersion(1);
        when(tenantMapper.selectOne(any()))
                .thenReturn(active, disabled);
        when(tenantMapper.update(any(), any())).thenReturn(1);
        when(tenantMemberMapper.selectCount(any())).thenReturn(1L);
        TenantStatusRequest request = new TenantStatusRequest();
        request.setStatus("DISABLED");

        service.updateStatus(
                platformTenantContext("9"),
                "9",
                request);

        AuditWriteRecord record = onlyRecord();
        assertEquals("tenant.status", record.getAction());
        assertEquals("TENANT:9", record.getTarget());
        assertEquals("TENANT", record.getScopeType());
        assertEquals(9L, record.getTenantId());
        JsonNode detail = objectMapper.readTree(record.getDetail());
        assertEquals("PLATFORM_TENANT",
                detail.path("contextType").asText());
        assertTrue(detail.path("platformManaged").asBoolean());
        assertEquals("9",
                detail.path("fields").path("tenantId").asText());
    }

    @Test
    void deniedAndFailedOutcomesRemainRedacted()
            throws Exception {
        TenantCreateRequest create = createRequest(CANARY_SECRET);
        assertThrows(
                ApiStatusException.class,
                () -> service.createTenant(
                        create,
                        tenantContext("9")));

        when(tenantMapper.selectOne(any()))
                .thenReturn(tenant(9L, "ACTIVE"));
        TenantStatusRequest invalid = new TenantStatusRequest();
        invalid.setStatus(CANARY_SECRET);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateStatus(
                        platformTenantContext("9"),
                        "9",
                        invalid));

        assertEquals(2, records.size());
        JsonNode denied =
                objectMapper.readTree(records.get(0).getDetail());
        JsonNode failed =
                objectMapper.readTree(records.get(1).getDetail());
        assertEquals("DENIED",
                denied.path("outcome").asText());
        assertEquals("FAILED",
                failed.path("outcome").asText());
        assertFalse(records.get(0).getDetail()
                .contains(CANARY_SECRET));
        assertFalse(records.get(1).getDetail()
                .contains(CANARY_SECRET));
        verify(tenantMapper, never()).update(any(), any());
    }

    private void assertAudited(
            Method method,
            String action,
            Audited.Scope scope) {
        Audited audited = method.getAnnotation(Audited.class);
        assertNotNull(audited);
        assertEquals(action, audited.action());
        assertEquals("TENANT", audited.targetType());
        assertEquals(scope, audited.scope());
        assertTrue(audited.recordDenied());
        assertTrue(audited.recordFailed());
        assertFalse(audited.includeArgs());
        assertFalse(audited.includeResult());
    }

    private void assertTargetAndDetailAllowlist(Method method) {
        Annotation[][] annotations =
                method.getParameterAnnotations();
        assertTrue(hasAnnotation(
                annotations[1],
                AuditTargetId.class));
        AuditDetail detail = annotation(
                annotations[1],
                AuditDetail.class);
        assertNotNull(detail);
        assertEquals("tenantId", detail.value());
        assertFalse(hasAnnotation(
                annotations[0],
                AuditDetail.class));
        assertFalse(hasAnnotation(
                annotations[2],
                AuditDetail.class));
    }

    private boolean hasAnnotation(
            Annotation[][] annotations,
            Class<? extends Annotation> type) {
        for (Annotation[] parameter : annotations) {
            if (hasAnnotation(parameter, type)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnnotation(
            Annotation[] annotations,
            Class<? extends Annotation> type) {
        return annotation(annotations, type) != null;
    }

    private <T extends Annotation> T annotation(
            Annotation[] annotations,
            Class<T> type) {
        for (Annotation current : annotations) {
            if (current.annotationType() == type) {
                return type.cast(current);
            }
        }
        return null;
    }

    private AuditWriteRecord onlyRecord() {
        assertEquals(1, records.size());
        return records.get(0);
    }

    private TenantCreateRequest createRequest(String name) {
        TenantCreateRequest request = new TenantCreateRequest();
        request.setClientRequestId("request-p15d-tenant");
        request.setTenantCode("tenant-a");
        request.setName(name);
        request.setTimezone("Asia/Shanghai");
        return request;
    }

    private UserRoleSnapshotVO activePlatformUser() {
        UserRoleSnapshotVO user = new UserRoleSnapshotVO();
        user.setUserId("1");
        user.setRole(0);
        user.setStatus(1);
        return user;
    }

    private Tenant tenant(Long id, String status) {
        Tenant tenant = new Tenant();
        tenant.setTenantId(id);
        tenant.setTenantCode("tenant-a");
        tenant.setName("租户");
        tenant.setStatus(status);
        tenant.setTimezone("Asia/Shanghai");
        tenant.setOwnerUserId(1L);
        tenant.setContextVersion(1L);
        tenant.setVersion(0);
        tenant.setDelFlag(0);
        return tenant;
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
                Collections.singletonList("TENANT_READ"),
                "request-1234567890");
    }

    private TrustedRequestContext platformTenantContext(
            String tenantId) {
        return TrustedRequestContext.user(
                TrustedSource.GATEWAY_USER,
                1L,
                0,
                "session-platform",
                "token-platform",
                TrustedContextType.PLATFORM_TENANT,
                tenantId,
                "tenant-a",
                null,
                1L,
                null,
                Collections.singletonList("TENANT_READ"),
                "request-1234567890");
    }

    private TrustedRequestContext tenantContext(String tenantId) {
        return TrustedRequestContext.user(
                TrustedSource.GATEWAY_USER,
                7L,
                1,
                "session-tenant",
                "token-tenant",
                TrustedContextType.TENANT,
                tenantId,
                "tenant-a",
                "TENANT_ADMIN",
                1L,
                1L,
                Collections.emptyList(),
                "request-1234567890");
    }

    private static final class TestTransactionManager
            extends AbstractPlatformTransactionManager {

        private static final long serialVersionUID = 1L;

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
