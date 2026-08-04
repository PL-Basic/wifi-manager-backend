package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.TenantCreateRequest;
import com.plagod.dto.tenant.TenantStatusRequest;
import com.plagod.dto.tenant.TenantUpdateRequest;
import com.plagod.vo.tenant.SaasPlanVO;
import com.plagod.vo.tenant.TenantMemberPageResult;
import com.plagod.vo.tenant.TenantPageResult;
import com.plagod.vo.tenant.TenantVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "tenant-service")
public interface TenantServiceClient {

    @GetMapping("/internal/admin/platform/tenants")
    ApiResponse<TenantPageResult> pageTenants(@RequestParam("current") long current,
                                              @RequestParam("size") long size,
                                              @RequestParam(value = "keyword", required = false) String keyword);

    @PostMapping("/internal/admin/platform/tenants")
    ApiResponse<TenantVO> createTenant(@RequestBody TenantCreateRequest request);

    @GetMapping("/internal/admin/platform/tenants/{tenantId}")
    ApiResponse<TenantVO> getTenant(@PathVariable("tenantId") String tenantId);

    @PutMapping("/internal/admin/platform/tenants/{tenantId}")
    ApiResponse<TenantVO> updateTenant(@PathVariable("tenantId") String tenantId,
                                       @RequestBody TenantUpdateRequest request);

    @PutMapping("/internal/admin/platform/tenants/{tenantId}/status")
    ApiResponse<TenantVO> updateStatus(@PathVariable("tenantId") String tenantId,
                                       @RequestBody TenantStatusRequest request);

    @GetMapping("/internal/admin/platform/tenants/{tenantId}/members")
    ApiResponse<TenantMemberPageResult> pageMembers(@PathVariable("tenantId") String tenantId,
                                                     @RequestParam("current") long current,
                                                     @RequestParam("size") long size);

    @GetMapping("/internal/admin/platform/saas-plans")
    ApiResponse<List<SaasPlanVO>> listPlans();
}
