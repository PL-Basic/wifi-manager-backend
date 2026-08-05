package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.TenantContextResolveRequest;
import com.plagod.dto.tenant.TenantContextValidationRequest;
import com.plagod.service.TenantContextService;
import com.plagod.vo.tenant.TenantContextVO;
import com.plagod.vo.tenant.TenantContextValidationVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/internal/tenants/context")
public class InternalTenantContextController {

    private final TenantContextService tenantContextService;

    public InternalTenantContextController(TenantContextService tenantContextService) {
        this.tenantContextService = tenantContextService;
    }

    @PostMapping("/resolve")
    public ApiResponse<TenantContextVO> resolve(@Valid @RequestBody TenantContextResolveRequest request) {
        return ApiResponse.success(tenantContextService.resolve(request));
    }

    @PostMapping("/validate")
    public ApiResponse<TenantContextValidationVO> validate(
            @Valid @RequestBody TenantContextValidationRequest request) {
        return ApiResponse.success(tenantContextService.validate(request));
    }
}
