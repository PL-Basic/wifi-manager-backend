package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.DefaultTenantMembershipRequest;
import com.plagod.service.TenantService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/internal/tenants/default-memberships")
public class InternalDefaultTenantController {

    private final TenantService tenantService;

    public InternalDefaultTenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ApiResponse<Void> ensureDefaultMembership(@Valid @RequestBody DefaultTenantMembershipRequest request) {
        tenantService.ensureDefaultMembership(request);
        return ApiResponse.success("默认租户成员关系已确认", null);
    }
}
