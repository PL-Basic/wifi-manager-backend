package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.TenantCreateRequest;
import com.plagod.dto.tenant.TenantStatusRequest;
import com.plagod.dto.tenant.TenantUpdateRequest;
import com.plagod.service.TenantService;
import com.plagod.vo.tenant.SaasPlanVO;
import com.plagod.vo.tenant.TenantMemberPageResult;
import com.plagod.vo.tenant.TenantPageResult;
import com.plagod.vo.tenant.TenantVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/internal/admin/platform")
public class InternalPlatformTenantController {

    private final TenantService tenantService;

    public InternalPlatformTenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/tenants")
    public ApiResponse<TenantPageResult> pageTenants(
            @RequestHeader("X-User-Role") Integer operatorRole,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        requireSuperAdmin(operatorRole);
        return ApiResponse.success(tenantService.pageTenants(current, size, keyword));
    }

    @PostMapping("/tenants")
    public ApiResponse<TenantVO> createTenant(
            @RequestHeader("X-User-Id") Long operatorId,
            @RequestHeader("X-User-Role") Integer operatorRole,
            @Valid @RequestBody TenantCreateRequest request) {
        return ApiResponse.success("租户创建成功", tenantService.createTenant(request, operatorId, operatorRole));
    }

    @GetMapping("/tenants/{tenantId}")
    public ApiResponse<TenantVO> getTenant(
            @RequestHeader("X-User-Role") Integer operatorRole,
            @PathVariable String tenantId) {
        requireSuperAdmin(operatorRole);
        return ApiResponse.success(tenantService.getTenant(tenantId));
    }

    @PutMapping("/tenants/{tenantId}")
    public ApiResponse<TenantVO> updateTenant(
            @RequestHeader("X-User-Role") Integer operatorRole,
            @PathVariable String tenantId,
            @Valid @RequestBody TenantUpdateRequest request) {
        return ApiResponse.success("租户信息修改成功", tenantService.updateTenant(tenantId, request, operatorRole));
    }

    @PutMapping("/tenants/{tenantId}/status")
    public ApiResponse<TenantVO> updateTenantStatus(
            @RequestHeader("X-User-Role") Integer operatorRole,
            @PathVariable String tenantId,
            @Valid @RequestBody TenantStatusRequest request) {
        return ApiResponse.success("租户状态修改成功", tenantService.updateStatus(tenantId, request, operatorRole));
    }

    @GetMapping("/tenants/{tenantId}/members")
    public ApiResponse<TenantMemberPageResult> pageMembers(
            @RequestHeader("X-User-Role") Integer operatorRole,
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        requireSuperAdmin(operatorRole);
        return ApiResponse.success(tenantService.pageMembers(tenantId, current, size));
    }

    @GetMapping("/saas-plans")
    public ApiResponse<List<SaasPlanVO>> listPlans(@RequestHeader("X-User-Role") Integer operatorRole) {
        requireSuperAdmin(operatorRole);
        return ApiResponse.success(tenantService.listPlans());
    }

    private void requireSuperAdmin(Integer role) {
        if (!Integer.valueOf(0).equals(role)) {
            throw com.plagod.exception.ApiStatusException.forbidden("仅超级管理员可以访问平台租户管理");
        }
    }
}
