package com.plagod.service.impl;

import com.plagod.client.UserRoleClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.DefaultTenantMembershipRequest;
import com.plagod.dto.tenant.TenantCreateRequest;
import com.plagod.dto.tenant.TenantStatusRequest;
import com.plagod.entity.Tenant;
import com.plagod.entity.TenantMember;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.SaasPlanMapper;
import com.plagod.mapper.TenantMapper;
import com.plagod.mapper.TenantMemberMapper;
import com.plagod.mapper.TenantSubscriptionMapper;
import com.plagod.vo.user.UserRoleSnapshotVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantServiceImplTest {

    private TenantMapper tenantMapper;
    private TenantMemberMapper tenantMemberMapper;
    private UserRoleClient userRoleClient;
    private TenantServiceImpl service;

    @BeforeEach
    void setUp() {
        tenantMapper = mock(TenantMapper.class);
        tenantMemberMapper = mock(TenantMemberMapper.class);
        userRoleClient = mock(UserRoleClient.class);
        service = new TenantServiceImpl(
                tenantMapper,
                tenantMemberMapper,
                mock(SaasPlanMapper.class),
                mock(TenantSubscriptionMapper.class),
                userRoleClient);
    }

    @Test
    void ordinaryAdminCannotCreateTenant() {
        ApiStatusException exception = assertThrows(ApiStatusException.class,
                () -> service.createTenant(new TenantCreateRequest(), 7L, 1));

        assertEquals(403, exception.getHttpStatus());
        verify(tenantMapper, never()).insert(any(Tenant.class));
    }

    @Test
    void defaultTenantCannotBeDisabled() {
        Tenant tenant = tenant(1L, "default-tenant");
        when(tenantMapper.selectById(1L)).thenReturn(tenant);
        TenantStatusRequest request = new TenantStatusRequest();
        request.setStatus("DISABLED");

        ApiStatusException exception = assertThrows(ApiStatusException.class,
                () -> service.updateStatus("1", request, 0));

        assertEquals(409, exception.getHttpStatus());
        verify(tenantMapper, never()).update(any(), any());
    }

    @Test
    void repeatedDefaultMembershipEventIsIdempotent() {
        UserRoleSnapshotVO user = new UserRoleSnapshotVO();
        user.setUserId("7");
        user.setRole(2);
        user.setStatus(1);
        when(userRoleClient.getRoleSnapshots(any()))
                .thenReturn(ApiResponse.success(Collections.singletonList(user)));

        Tenant tenant = tenant(1L, "default-tenant");
        when(tenantMapper.selectOne(any())).thenReturn(tenant);

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
}
