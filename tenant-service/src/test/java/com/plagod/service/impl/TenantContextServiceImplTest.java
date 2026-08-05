package com.plagod.service.impl;

import com.plagod.client.UserRoleClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.TenantContextResolveRequest;
import com.plagod.dto.tenant.TenantContextValidationRequest;
import com.plagod.entity.PlatformStaff;
import com.plagod.entity.Tenant;
import com.plagod.entity.TenantMember;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.PlatformStaffMapper;
import com.plagod.mapper.TenantMapper;
import com.plagod.mapper.TenantMemberMapper;
import com.plagod.vo.tenant.TenantContextVO;
import com.plagod.vo.tenant.TenantContextValidationVO;
import com.plagod.vo.user.UserRoleSnapshotVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantContextServiceImplTest {

    private TenantMapper tenantMapper;
    private TenantMemberMapper tenantMemberMapper;
    private PlatformStaffMapper platformStaffMapper;
    private UserRoleClient userRoleClient;
    private TenantContextServiceImpl service;

    @BeforeEach
    void setUp() {
        tenantMapper = mock(TenantMapper.class);
        tenantMemberMapper = mock(TenantMemberMapper.class);
        platformStaffMapper = mock(PlatformStaffMapper.class);
        userRoleClient = mock(UserRoleClient.class);
        service = new TenantContextServiceImpl(
                tenantMapper,
                tenantMemberMapper,
                platformStaffMapper,
                userRoleClient);
        when(platformStaffMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    @Test
    void roleZeroDefaultsToPlatformWithActiveAuthoritiesOnly() {
        mockCurrentUser(1L, 0);
        PlatformStaff disabled = staff("TENANT_READ", "DISABLED");
        PlatformStaff active = staff("SUPPORT_DEVELOPER", "ACTIVE");
        when(platformStaffMapper.selectList(any())).thenReturn(Arrays.asList(disabled, active));

        TenantContextVO context = service.resolve(resolveRequest(1L, 0, null, null));

        assertEquals("PLATFORM", context.getContextType());
        assertNull(context.getTenantId());
        assertEquals(Collections.singletonList("SUPPORT_DEVELOPER"), context.getAuthorities());
        assertTrue(context.getWritable());
    }

    @Test
    void ordinaryUserDefaultsToActiveTenantWithDualVersions() {
        mockCurrentUser(7L, 2);
        when(tenantMemberMapper.selectOne(any())).thenReturn(member(7L, 11L, "MEMBER", 5L));
        when(tenantMapper.selectById(11L)).thenReturn(tenant(11L, "tenant-a", "ACTIVE", 9L));

        TenantContextVO context = service.resolve(resolveRequest(7L, 2, null, null));

        assertEquals("TENANT", context.getContextType());
        assertEquals("11", context.getTenantId());
        assertEquals("MEMBER", context.getTenantRole());
        assertEquals(9L, context.getContextVersion());
        assertEquals(5L, context.getMemberContextVersion());
        assertTrue(context.getWritable());
    }

    @Test
    void roleZeroPlatformTenantDoesNotForgeMembership() {
        mockCurrentUser(1L, 0);
        when(tenantMapper.selectById(22L)).thenReturn(tenant(22L, "tenant-b", "DISABLED", 3L));

        TenantContextVO context = service.resolve(
                resolveRequest(1L, 0, "PLATFORM_TENANT", "22"));

        assertEquals("PLATFORM_TENANT", context.getContextType());
        assertEquals("22", context.getTenantId());
        assertNull(context.getTenantRole());
        assertNull(context.getMemberContextVersion());
        assertFalse(context.getWritable());
        verify(tenantMemberMapper, never()).selectOne(any());
    }

    @Test
    void ordinaryUserCannotResolvePlatformContext() {
        mockCurrentUser(7L, 2);

        ApiStatusException exception = assertThrows(ApiStatusException.class,
                () -> service.resolve(resolveRequest(7L, 2, "PLATFORM", null)));

        assertEquals(403, exception.getHttpStatus());
    }

    @Test
    void staleWriteVersionReturnsUnauthorized() {
        mockCurrentUser(7L, 2);
        when(tenantMemberMapper.selectOne(any())).thenReturn(member(7L, 11L, "MEMBER", 5L));
        when(tenantMapper.selectById(11L)).thenReturn(tenant(11L, "tenant-a", "ACTIVE", 9L));
        TenantContextValidationRequest request = validationRequest(true);
        request.setContextVersion(8L);

        ApiStatusException exception = assertThrows(ApiStatusException.class,
                () -> service.validate(request));

        assertEquals(401, exception.getHttpStatus());
    }

    @Test
    void staleReadReturnsCurrentTrustedDisabledContext() {
        mockCurrentUser(7L, 2);
        when(tenantMemberMapper.selectOne(any())).thenReturn(member(7L, 11L, "MEMBER", 5L));
        when(tenantMapper.selectById(11L)).thenReturn(tenant(11L, "tenant-a", "DISABLED", 9L));
        TenantContextValidationRequest request = validationRequest(false);
        request.setTenantCode("old-code");
        request.setTenantRole("TENANT_ADMIN");
        request.setContextVersion(1L);
        request.setMemberContextVersion(1L);

        TenantContextValidationVO result = service.validate(request);

        assertTrue(result.getAllowed());
        assertEquals("tenant-a", result.getContext().getTenantCode());
        assertEquals("MEMBER", result.getContext().getTenantRole());
        assertEquals(9L, result.getContext().getContextVersion());
        assertEquals(5L, result.getContext().getMemberContextVersion());
        assertFalse(result.getContext().getWritable());
    }

    @Test
    void disabledTenantWriteReturnsForbidden() {
        mockCurrentUser(7L, 2);
        when(tenantMemberMapper.selectOne(any())).thenReturn(member(7L, 11L, "MEMBER", 5L));
        when(tenantMapper.selectById(11L)).thenReturn(tenant(11L, "tenant-a", "DISABLED", 9L));

        ApiStatusException exception = assertThrows(ApiStatusException.class,
                () -> service.validate(validationRequest(true)));

        assertEquals(403, exception.getHttpStatus());
    }

    @Test
    void removedMembershipReturnsForbidden() {
        mockCurrentUser(7L, 2);
        when(tenantMemberMapper.selectOne(any())).thenReturn(null);

        ApiStatusException exception = assertThrows(ApiStatusException.class,
                () -> service.validate(validationRequest(false)));

        assertEquals(403, exception.getHttpStatus());
    }

    @Test
    void userRoleDependencyFailureReturnsServiceUnavailable() {
        when(userRoleClient.getRoleSnapshots(any())).thenThrow(new IllegalStateException("down"));

        ApiStatusException exception = assertThrows(ApiStatusException.class,
                () -> service.resolve(resolveRequest(7L, 2, null, null)));

        assertEquals(503, exception.getHttpStatus());
    }

    private void mockCurrentUser(Long userId, Integer role) {
        UserRoleSnapshotVO user = new UserRoleSnapshotVO();
        user.setUserId(String.valueOf(userId));
        user.setRole(role);
        user.setStatus(1);
        when(userRoleClient.getRoleSnapshots(any()))
                .thenReturn(ApiResponse.success(Collections.singletonList(user)));
    }

    private TenantContextResolveRequest resolveRequest(Long userId,
                                                       Integer role,
                                                       String contextType,
                                                       String tenantId) {
        TenantContextResolveRequest request = new TenantContextResolveRequest();
        request.setUserId(String.valueOf(userId));
        request.setGlobalRole(role);
        request.setContextType(contextType);
        request.setTenantId(tenantId);
        return request;
    }

    private TenantContextValidationRequest validationRequest(boolean writeRequest) {
        TenantContextValidationRequest request = new TenantContextValidationRequest();
        request.setUserId("7");
        request.setGlobalRole(2);
        request.setContextType("TENANT");
        request.setTenantId("11");
        request.setTenantCode("tenant-a");
        request.setTenantRole("MEMBER");
        request.setContextVersion(9L);
        request.setMemberContextVersion(5L);
        request.setAuthorities(Collections.emptyList());
        request.setWriteRequest(writeRequest);
        return request;
    }

    private Tenant tenant(Long tenantId, String tenantCode, String status, Long contextVersion) {
        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setTenantCode(tenantCode);
        tenant.setName("测试租户");
        tenant.setStatus(status);
        tenant.setContextVersion(contextVersion);
        return tenant;
    }

    private TenantMember member(Long userId, Long tenantId, String tenantRole, Long contextVersion) {
        TenantMember member = new TenantMember();
        member.setUserId(userId);
        member.setTenantId(tenantId);
        member.setTenantRole(tenantRole);
        member.setStatus("ACTIVE");
        member.setIsDefault(1);
        member.setContextVersion(contextVersion);
        return member;
    }

    private PlatformStaff staff(String authority, String status) {
        PlatformStaff staff = new PlatformStaff();
        staff.setAuthority(authority);
        staff.setStatus(status);
        return staff;
    }
}
