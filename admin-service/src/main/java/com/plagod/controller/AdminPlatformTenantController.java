package com.plagod.controller;

import com.plagod.client.TenantServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.TenantCreateRequest;
import com.plagod.dto.tenant.TenantStatusRequest;
import com.plagod.dto.tenant.TenantUpdateRequest;
import com.plagod.exception.ApiStatusException;
import com.plagod.vo.tenant.SaasPlanVO;
import com.plagod.vo.tenant.TenantMemberPageResult;
import com.plagod.vo.tenant.TenantPageResult;
import com.plagod.vo.tenant.TenantVO;
import feign.FeignException;
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
import java.util.function.Supplier;

@RestController
@RequestMapping("/admin/platform")
public class AdminPlatformTenantController {

    private final TenantServiceClient tenantServiceClient;

    public AdminPlatformTenantController(TenantServiceClient tenantServiceClient) {
        this.tenantServiceClient = tenantServiceClient;
    }

    @GetMapping("/tenants")
    public ApiResponse<TenantPageResult> pageTenants(
            @RequestHeader("X-User-Role") Integer role,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        requireSuperAdmin(role);
        return callTenantService(() -> tenantServiceClient.pageTenants(current, size, keyword));
    }

    @PostMapping("/tenants")
    public ApiResponse<TenantVO> createTenant(
            @RequestHeader("X-User-Role") Integer role,
            @Valid @RequestBody TenantCreateRequest request) {
        requireSuperAdmin(role);
        return callTenantService(() -> tenantServiceClient.createTenant(request));
    }

    @GetMapping("/tenants/{tenantId}")
    public ApiResponse<TenantVO> getTenant(
            @RequestHeader("X-User-Role") Integer role,
            @PathVariable String tenantId) {
        requireSuperAdmin(role);
        return callTenantService(() -> tenantServiceClient.getTenant(tenantId));
    }

    @PutMapping("/tenants/{tenantId}")
    public ApiResponse<TenantVO> updateTenant(
            @RequestHeader("X-User-Role") Integer role,
            @PathVariable String tenantId,
            @Valid @RequestBody TenantUpdateRequest request) {
        requireSuperAdmin(role);
        return callTenantService(() -> tenantServiceClient.updateTenant(tenantId, request));
    }

    @PutMapping("/tenants/{tenantId}/status")
    public ApiResponse<TenantVO> updateStatus(
            @RequestHeader("X-User-Role") Integer role,
            @PathVariable String tenantId,
            @Valid @RequestBody TenantStatusRequest request) {
        requireSuperAdmin(role);
        return callTenantService(() -> tenantServiceClient.updateStatus(tenantId, request));
    }

    @GetMapping("/tenants/{tenantId}/members")
    public ApiResponse<TenantMemberPageResult> pageMembers(
            @RequestHeader("X-User-Role") Integer role,
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        requireSuperAdmin(role);
        return callTenantService(() -> tenantServiceClient.pageMembers(tenantId, current, size));
    }

    @GetMapping("/saas-plans")
    public ApiResponse<List<SaasPlanVO>> listPlans(@RequestHeader("X-User-Role") Integer role) {
        requireSuperAdmin(role);
        return callTenantService(tenantServiceClient::listPlans);
    }

    private void requireSuperAdmin(Integer role) {
        if (!Integer.valueOf(0).equals(role)) {
            throw ApiStatusException.forbidden("仅超级管理员可以访问平台租户管理");
        }
    }

    private <T> ApiResponse<T> callTenantService(Supplier<ApiResponse<T>> request) {
        try {
            return request.get();
        } catch (FeignException exception) {
            int status = exception.status();
            if (status == 400) {
                throw new IllegalArgumentException("平台租户请求参数无效");
            }
            if (status == 403) {
                throw ApiStatusException.forbidden("仅超级管理员可以执行平台租户操作");
            }
            if (status == 404) {
                throw ApiStatusException.notFound("租户不存在");
            }
            if (status == 409) {
                throw ApiStatusException.conflict("租户编码、成员关系或租户状态发生冲突");
            }
            throw ApiStatusException.serviceUnavailable("租户服务暂时不可用");
        }
    }
}
