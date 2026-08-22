package com.plagod.service;

import com.plagod.dto.tenant.DefaultTenantMembershipRequest;
import com.plagod.dto.tenant.TenantCreateRequest;
import com.plagod.dto.tenant.TenantStatusRequest;
import com.plagod.dto.tenant.TenantUpdateRequest;
import com.plagod.security.TrustedRequestContext;
import com.plagod.vo.tenant.SaasPlanVO;
import com.plagod.vo.tenant.MyTenantVO;
import com.plagod.vo.tenant.TenantMemberPageResult;
import com.plagod.vo.tenant.TenantPageResult;
import com.plagod.vo.tenant.TenantVO;

import java.util.List;

public interface TenantService {
    TenantPageResult pageTenants(TrustedRequestContext context,
                                 Integer current,
                                 Integer size,
                                 String keyword);

    TenantVO getTenant(TrustedRequestContext context, String tenantId);

    TenantMemberPageResult pageMembers(TrustedRequestContext context,
                                       String tenantId,
                                       Integer current,
                                       Integer size);

    List<SaasPlanVO> listPlans(TrustedRequestContext context);

    List<MyTenantVO> listMyTenants(Long userId);

    TenantVO createTenant(TenantCreateRequest request,
                          TrustedRequestContext context);

    TenantVO updateTenant(TrustedRequestContext context,
                          String tenantId,
                          TenantUpdateRequest request);

    TenantVO updateStatus(TrustedRequestContext context,
                          String tenantId,
                          TenantStatusRequest request);

    void ensureDefaultMembership(DefaultTenantMembershipRequest request);
}
