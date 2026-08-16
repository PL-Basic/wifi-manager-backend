package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.TenantCreateRequest;
import com.plagod.dto.tenant.TenantStatusRequest;
import com.plagod.dto.tenant.TenantUpdateRequest;
import com.plagod.security.TenantRequestContextProvider;
import com.plagod.security.TrustedRequestContext;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/internal/admin/platform")
public class InternalPlatformTenantController {

    private final TenantService tenantService;
    private final TenantRequestContextProvider contextProvider;

    public InternalPlatformTenantController(
            TenantService tenantService,
            TenantRequestContextProvider contextProvider) {
        this.tenantService = tenantService;
        this.contextProvider = contextProvider;
    }

    @GetMapping("/tenants")
    public ApiResponse<TenantPageResult> pageTenants(
            HttpServletRequest servletRequest,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        TrustedRequestContext context =
                contextProvider.requireUserContext(servletRequest);
        return ApiResponse.success(
                tenantService.pageTenants(context, current, size, keyword));
    }

    @PostMapping("/tenants")
    public ApiResponse<TenantVO> createTenant(
            HttpServletRequest servletRequest,
            @Valid @RequestBody TenantCreateRequest request) {
        TrustedRequestContext context =
                contextProvider.requireUserContext(servletRequest);
        return ApiResponse.success(
                "租户创建成功",
                tenantService.createTenant(request, context));
    }

    @GetMapping("/tenants/{tenantId}")
    public ApiResponse<TenantVO> getTenant(
            HttpServletRequest servletRequest,
            @PathVariable String tenantId) {
        TrustedRequestContext context =
                contextProvider.requireUserContext(servletRequest);
        return ApiResponse.success(
                tenantService.getTenant(context, tenantId));
    }

    @PutMapping("/tenants/{tenantId}")
    public ApiResponse<TenantVO> updateTenant(
            HttpServletRequest servletRequest,
            @PathVariable String tenantId,
            @Valid @RequestBody TenantUpdateRequest request) {
        TrustedRequestContext context =
                contextProvider.requireUserContext(servletRequest);
        return ApiResponse.success(
                "租户信息修改成功",
                tenantService.updateTenant(context, tenantId, request));
    }

    @PutMapping("/tenants/{tenantId}/status")
    public ApiResponse<TenantVO> updateTenantStatus(
            HttpServletRequest servletRequest,
            @PathVariable String tenantId,
            @Valid @RequestBody TenantStatusRequest request) {
        TrustedRequestContext context =
                contextProvider.requireUserContext(servletRequest);
        return ApiResponse.success(
                "租户状态修改成功",
                tenantService.updateStatus(context, tenantId, request));
    }

    @GetMapping("/tenants/{tenantId}/members")
    public ApiResponse<TenantMemberPageResult> pageMembers(
            HttpServletRequest servletRequest,
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        TrustedRequestContext context =
                contextProvider.requireUserContext(servletRequest);
        return ApiResponse.success(
                tenantService.pageMembers(
                        context,
                        tenantId,
                        current,
                        size));
    }

    @GetMapping("/saas-plans")
    public ApiResponse<List<SaasPlanVO>> listPlans(
            HttpServletRequest servletRequest) {
        TrustedRequestContext context =
                contextProvider.requireUserContext(servletRequest);
        return ApiResponse.success(tenantService.listPlans(context));
    }
}
