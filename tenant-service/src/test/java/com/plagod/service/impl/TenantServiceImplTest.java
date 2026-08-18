package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.client.UserRoleClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.DefaultTenantMembershipRequest;
import com.plagod.dto.tenant.TenantCreateRequest;
import com.plagod.dto.tenant.TenantStatusRequest;
import com.plagod.entity.Tenant;
import com.plagod.entity.TenantCreationReceipt;
import com.plagod.entity.TenantDomainOutbox;
import com.plagod.entity.TenantMember;
import com.plagod.exception.ApiStatusException;
import com.plagod.exception.ApiErrorKey;
import com.plagod.mapper.SaasPlanMapper;
import com.plagod.mapper.TenantCreationReceiptMapper;
import com.plagod.mapper.TenantDomainOutboxMapper;
import com.plagod.mapper.TenantMapper;
import com.plagod.mapper.TenantMemberMapper;
import com.plagod.mapper.TenantSubscriptionMapper;
import com.plagod.security.TrustedContextType;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.TrustedSource;
import com.plagod.support.StableUnits;
import com.plagod.vo.tenant.TenantMemberPageResult;
import com.plagod.vo.tenant.TenantPageResult;
import com.plagod.vo.user.UserRoleSnapshotVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantServiceImplTest {

    private TenantMapper tenantMapper;
    private TenantMemberMapper tenantMemberMapper;
    private TenantCreationReceiptMapper creationReceiptMapper;
    private TenantDomainOutboxMapper tenantDomainOutboxMapper;
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
        userRoleClient = mock(UserRoleClient.class);
        service = new TenantServiceImpl(
                tenantMapper,
                tenantMemberMapper,
                mock(SaasPlanMapper.class),
                mock(TenantSubscriptionMapper.class),
                creationReceiptMapper,
                tenantDomainOutboxMapper,
                userRoleClient,
                new TestTransactionManager());
    }

    @Test
    void ordinaryAdminCannotCreateTenant() {
        ApiStatusException exception = assertThrows(ApiStatusException.class,
                () -> service.createTenant(
                        new TenantCreateRequest(),
                        tenantContext("1", "TENANT_ADMIN")));

        assertEquals(403, exception.getHttpStatus());
        verify(tenantMapper, never()).insert(any(Tenant.class));
    }

    @Test
    void createTenantReplaysNormalizedInputWithoutDuplicateWrites() {
        AtomicReference<TenantCreationReceipt> storedReceipt =
                new AtomicReference<>();
        AtomicReference<Tenant> storedTenant =
                new AtomicReference<>();
        when(creationReceiptMapper.selectOne(any()))
                .thenAnswer(invocation -> storedReceipt.get());
        when(tenantMapper.selectById(41L))
                .thenAnswer(invocation -> storedTenant.get());
        when(tenantMemberMapper.selectCount(any())).thenReturn(1L);
        when(userRoleClient.getRoleSnapshots(any()))
                .thenAnswer(invocation -> {
                    assertFalse(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return ApiResponse.success(
                            Collections.singletonList(
                                    activePlatformUser()));
                });
        when(tenantMapper.insert(any(Tenant.class)))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    Tenant tenant = invocation.getArgument(0);
                    tenant.setTenantId(41L);
                    storedTenant.set(tenant);
                    return 1;
                });
        when(tenantMemberMapper.insert(any(TenantMember.class)))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return 1;
                });
        when(tenantDomainOutboxMapper.insert(
                any(TenantDomainOutbox.class)))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return 1;
                });
        when(creationReceiptMapper.insert(
                any(TenantCreationReceipt.class)))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    storedReceipt.set(invocation.getArgument(0));
                    return 1;
                });

        TenantCreateRequest first =
                createRequest("request-tenant-1",
                        "  示例租户  ",
                        "  Asia/Shanghai  ");
        TenantCreateRequest replay =
                createRequest("request-tenant-1",
                        "示例租户",
                        "Asia/Shanghai");

        assertEquals("41",
                service.createTenant(
                        first,
                        platformContext()).getTenantId());
        assertEquals("41",
                service.createTenant(
                        replay,
                        platformContext()).getTenantId());

        verify(userRoleClient).getRoleSnapshots(any());
        verify(tenantMapper).insert(any(Tenant.class));
        verify(tenantMemberMapper)
                .insert(any(TenantMember.class));
        verify(tenantDomainOutboxMapper)
                .insert(any(TenantDomainOutbox.class));
        verify(creationReceiptMapper)
                .insert(any(TenantCreationReceipt.class));

        TenantCreationReceipt receipt = storedReceipt.get();
        assertEquals(1L, receipt.getPlatformActorId());
        assertEquals("request-tenant-1",
                receipt.getClientRequestId());
        assertEquals(64,
                receipt.getRequestFingerprint().length());
        assertEquals(41L, receipt.getTargetTenantId());
        assertEquals("APPLIED", receipt.getReceiptStatus());

        ArgumentCaptor<TenantDomainOutbox> outboxCaptor =
                ArgumentCaptor.forClass(TenantDomainOutbox.class);
        verify(tenantDomainOutboxMapper)
                .insert(outboxCaptor.capture());
        assertEquals(64, outboxCaptor.getValue()
                .getEventId().length());
        assertEquals("TENANT_CREATED",
                outboxCaptor.getValue().getEventType());
        assertEquals("PENDING",
                outboxCaptor.getValue().getOutboxStatus());
    }

    @Test
    void reusedClientRequestIdWithDifferentInputConflicts() {
        TenantCreationReceipt receipt =
                new TenantCreationReceipt();
        receipt.setPlatformActorId(1L);
        receipt.setClientRequestId("request-tenant-2");
        receipt.setRequestFingerprint(
                "0000000000000000000000000000000000000000000000000000000000000000");
        receipt.setTargetTenantId(41L);
        receipt.setReceiptStatus("APPLIED");
        when(creationReceiptMapper.selectOne(any()))
                .thenReturn(receipt);

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.createTenant(
                        createRequest(
                                "request-tenant-2",
                                "不同租户",
                                "Asia/Shanghai"),
                        platformContext()));

        assertEquals(409, exception.getHttpStatus());
        assertEquals(
                ApiErrorKey.IDEMPOTENCY_KEY_CONFLICT.value(),
                exception.getErrorKey());
        verify(userRoleClient, never())
                .getRoleSnapshots(any());
        verify(tenantMapper, never())
                .insert(any(Tenant.class));
        verify(tenantDomainOutboxMapper, never())
                .insert(any(TenantDomainOutbox.class));
    }

    @Test
    void clientRequestIdNeverFallsBackToRequestContext() {
        TenantCreateRequest request = createRequest(
                null,
                "示例租户",
                "Asia/Shanghai");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createTenant(
                        request,
                        platformContext()));

        verify(creationReceiptMapper, never())
                .selectOne(any());
        verify(userRoleClient, never())
                .getRoleSnapshots(any());
        verify(tenantMapper, never())
                .insert(any(Tenant.class));
    }

    @Test
    void defaultTenantCannotBeDisabled() {
        Tenant tenant = tenant(1L, "default-tenant");
        when(tenantMapper.selectOne(any())).thenReturn(tenant);
        TenantStatusRequest request = new TenantStatusRequest();
        request.setStatus("DISABLED");

        ApiStatusException exception = assertThrows(ApiStatusException.class,
                () -> service.updateStatus(
                        platformTenantContext("1"),
                        "1",
                        request));

        assertEquals(409, exception.getHttpStatus());
        verify(tenantMapper, never()).update(any(), any());
    }

    @Test
    void invalidTenantStatusIsRejectedBeforePersistence() {
        when(tenantMapper.selectOne(any()))
                .thenReturn(tenant(9L, "tenant-a"));
        TenantStatusRequest request = new TenantStatusRequest();
        request.setStatus("PAUSED");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateStatus(
                        tenantContext("9", "TENANT_ADMIN"),
                        "9",
                        request));

        assertEquals(
                "租户状态只能是ACTIVE或DISABLED",
                exception.getMessage());
        verify(tenantMapper, never()).update(any(), any());
    }

    @Test
    void repeatedTenantStatusDoesNotWriteAgain() {
        when(tenantMapper.selectOne(any()))
                .thenReturn(tenant(9L, "tenant-a"));
        when(tenantMemberMapper.selectCount(any())).thenReturn(1L);
        TenantStatusRequest request = new TenantStatusRequest();
        request.setStatus("ACTIVE");

        assertEquals(
                "ACTIVE",
                service.updateStatus(
                        tenantContext("9", "TENANT_ADMIN"),
                        "9",
                        request).getStatus());

        verify(tenantMapper, never()).update(any(), any());
    }

    @Test
    void concurrentStatusChangeUsesLoadedStateAndVersionThenConflicts() {
        Tenant current = tenant(9L, "tenant-a");
        current.setVersion(3);
        when(tenantMapper.selectOne(any())).thenReturn(current);
        AtomicReference<UpdateWrapper<Tenant>> update =
                new AtomicReference<>();
        when(tenantMapper.update(any(), any()))
                .thenAnswer(invocation -> {
                    update.set(invocation.getArgument(1));
                    return 0;
                });
        TenantStatusRequest request = new TenantStatusRequest();
        request.setStatus("DISABLED");

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.updateStatus(
                        platformTenantContext("9"),
                        "9",
                        request));

        assertEquals(409, exception.getHttpStatus());
        assertTrue(update.get().getSqlSegment().contains("status"));
        assertTrue(update.get().getSqlSegment().contains("version"));
        assertTrue(update.get().getParamNameValuePairs()
                .containsValue("ACTIVE"));
        assertTrue(update.get().getParamNameValuePairs()
                .containsValue(3));
    }

    @Test
    void repeatedDefaultMembershipEventIsIdempotent() {
        UserRoleSnapshotVO user = new UserRoleSnapshotVO();
        user.setUserId("7");
        user.setRole(2);
        user.setStatus(1);
        when(userRoleClient.getRoleSnapshots(any()))
                .thenAnswer(invocation -> {
                    assertFalse(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return ApiResponse.success(
                            Collections.singletonList(user));
                });

        Tenant tenant = tenant(1L, "default-tenant");
        when(tenantMapper.selectOne(any()))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return tenant;
                });

        TenantMember member = new TenantMember();
        member.setMemberId(11L);
        member.setTenantId(1L);
        member.setUserId(7L);
        member.setTenantRole("MEMBER");
        member.setStatus("ACTIVE");
        member.setIsDefault(1);
        member.setVersion(0);
        when(tenantMemberMapper.selectOne(any())).thenReturn(member);

        DefaultTenantMembershipRequest request = new DefaultTenantMembershipRequest();
        request.setEventId("event-1");
        request.setUserId(7L);
        request.setRole(2);

        assertDoesNotThrow(() -> service.ensureDefaultMembership(request));
        verify(tenantMemberMapper, never()).insert(any(TenantMember.class));
        verify(tenantMemberMapper, never()).update(any(), any());
        verify(tenantDomainOutboxMapper, never())
                .insert(any(TenantDomainOutbox.class));
    }

    @Test
    void tenantPagingUsesSharedPageBounds() {
        when(tenantMapper.selectPage(any(Page.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantMemberMapper.selectPage(any(Page.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantMapper.selectOne(any()))
                .thenReturn(tenant(1L, "tenant-a"));

        TenantPageResult tenants =
                service.pageTenants(platformContext(), -1, 0, null);
        TenantMemberPageResult members = service.pageMembers(
                tenantContext("1", "TENANT_ADMIN"),
                "1",
                0,
                101);

        assertEquals(1, tenants.getCurrent());
        assertEquals(10, tenants.getSize());
        assertEquals(1, members.getCurrent());
        assertEquals(100, members.getSize());
    }

    @Test
    void tenantResourceLoadUsesContextTenantAndBusinessId() {
        when(tenantMapper.selectOne(any()))
                .thenReturn(tenant(11L, "tenant-a"));

        service.getTenant(
                tenantContext("11", "TENANT_ADMIN"),
                "11");

        ArgumentCaptor<QueryWrapper<Tenant>> queryCaptor =
                ArgumentCaptor.forClass(QueryWrapper.class);
        verify(tenantMapper).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment()
                .contains("tenant_id"));
        Map<String, Object> values =
                queryCaptor.getValue().getParamNameValuePairs();
        assertEquals(2, values.size());
        assertTrue(values.containsValue(11L));
    }

    @Test
    void tenantAReadingTenantBReturnsNotFound() {
        when(tenantMapper.selectOne(any())).thenReturn(null);

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.getTenant(
                        tenantContext("11", "TENANT_ADMIN"),
                        "22"));

        assertEquals(404, exception.getHttpStatus());
        ArgumentCaptor<QueryWrapper<Tenant>> queryCaptor =
                ArgumentCaptor.forClass(QueryWrapper.class);
        verify(tenantMapper).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment()
                .contains("tenant_id"));
        assertTrue(queryCaptor.getValue().getParamNameValuePairs()
                .containsValue(11L));
        assertTrue(queryCaptor.getValue().getParamNameValuePairs()
                .containsValue(22L));
    }

    @Test
    void platformCannotDirectlyReadTenantResource() {
        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.getTenant(platformContext(), "11"));

        assertEquals(403, exception.getHttpStatus());
        verify(tenantMapper, never()).selectOne(any());
    }

    @Test
    void internalTokenAloneCannotReadTenantResource() {
        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.getTenant(
                        TrustedRequestContext.internalService(
                                "request-1234567890"),
                        "11"));

        assertEquals(403, exception.getHttpStatus());
        verify(tenantMapper, never()).selectOne(any());
    }

    @Test
    void platformTenantCanReadOnlyItsTargetTenant() {
        when(tenantMapper.selectOne(any()))
                .thenReturn(tenant(11L, "tenant-a"));

        assertDoesNotThrow(() -> service.getTenant(
                platformTenantContext("11"),
                "11"));
    }

    @Test
    void memberCannotUpdateTenantResource() {
        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.updateTenant(
                        tenantContext("11", "MEMBER"),
                        "11",
                        null));

        assertEquals(403, exception.getHttpStatus());
        verify(tenantMapper, never()).selectOne(any());
    }

    @Test
    void newDefaultMembershipUsesSharedBusinessZone() {
        UserRoleSnapshotVO user = new UserRoleSnapshotVO();
        user.setUserId("7");
        user.setRole(2);
        user.setStatus(1);
        when(userRoleClient.getRoleSnapshots(any()))
                .thenAnswer(invocation -> {
                    assertFalse(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return ApiResponse.success(
                            Collections.singletonList(user));
                });
        when(tenantMapper.selectOne(any()))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return tenant(1L, "default-tenant");
                });
        when(tenantMemberMapper.selectOne(any())).thenReturn(null);
        when(tenantMemberMapper.insert(any(TenantMember.class)))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return 1;
                });

        DefaultTenantMembershipRequest request = new DefaultTenantMembershipRequest();
        request.setEventId("event-2");
        request.setUserId(7L);
        request.setRole(2);
        LocalDateTime before = LocalDateTime.now(StableUnits.ASIA_SHANGHAI).minusSeconds(1);

        service.ensureDefaultMembership(request);

        LocalDateTime after = LocalDateTime.now(StableUnits.ASIA_SHANGHAI).plusSeconds(1);
        ArgumentCaptor<TenantMember> memberCaptor = ArgumentCaptor.forClass(TenantMember.class);
        verify(tenantMemberMapper).insert(memberCaptor.capture());
        assertFalse(memberCaptor.getValue().getJoinTime().isBefore(before));
        assertFalse(memberCaptor.getValue().getJoinTime().isAfter(after));
        verify(tenantDomainOutboxMapper, never())
                .insert(any(TenantDomainOutbox.class));
    }

    private TenantCreateRequest createRequest(
            String clientRequestId,
            String name,
            String timezone) {
        TenantCreateRequest request = new TenantCreateRequest();
        request.setClientRequestId(clientRequestId);
        request.setTenantCode("tenant-a");
        request.setName(name);
        request.setTimezone(timezone);
        return request;
    }

    private UserRoleSnapshotVO activePlatformUser() {
        UserRoleSnapshotVO user = new UserRoleSnapshotVO();
        user.setUserId("1");
        user.setRole(0);
        user.setStatus(1);
        return user;
    }

    private Tenant tenant(Long id, String code) {
        Tenant tenant = new Tenant();
        tenant.setTenantId(id);
        tenant.setTenantCode(code);
        tenant.setName("租户");
        tenant.setStatus("ACTIVE");
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

    private TrustedRequestContext platformTenantContext(String tenantId) {
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

    private TrustedRequestContext tenantContext(
            String tenantId,
            String tenantRole) {
        return TrustedRequestContext.user(
                TrustedSource.GATEWAY_USER,
                7L,
                1,
                "session-tenant",
                "token-tenant",
                TrustedContextType.TENANT,
                tenantId,
                "tenant-a",
                tenantRole,
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
