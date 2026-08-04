package com.plagod.service;

import com.plagod.dto.tenant.DefaultTenantMembershipRequest;
import com.plagod.dto.tenant.TenantCreateRequest;
import com.plagod.dto.tenant.TenantStatusRequest;
import com.plagod.dto.tenant.TenantUpdateRequest;
import com.plagod.vo.tenant.SaasPlanVO;
import com.plagod.vo.tenant.MyTenantVO;
import com.plagod.vo.tenant.TenantMemberPageResult;
import com.plagod.vo.tenant.TenantPageResult;
import com.plagod.vo.tenant.TenantVO;

import java.util.List;

public interface TenantService {
    TenantPageResult pageTenants(long current, long size, String keyword);

    TenantVO getTenant(String tenantId);

    TenantMemberPageResult pageMembers(String tenantId, long current, long size);

    List<SaasPlanVO> listPlans();

    List<MyTenantVO> listMyTenants(Long userId);

    TenantVO createTenant(TenantCreateRequest request, Long operatorId, Integer operatorRole);

    TenantVO updateTenant(String tenantId, TenantUpdateRequest request, Integer operatorRole);

    TenantVO updateStatus(String tenantId, TenantStatusRequest request, Integer operatorRole);

    void ensureDefaultMembership(DefaultTenantMembershipRequest request);
}
