package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.TenantContextValidationRequest;
import com.plagod.security.TenantRequestContextProvider;
import com.plagod.security.TrustedRequestContext;
import com.plagod.service.TenantContextService;
import com.plagod.service.TenantService;
import com.plagod.vo.tenant.MyTenantVO;
import com.plagod.vo.tenant.TenantContextVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/tenants")
public class TenantController {

    private final TenantService tenantService;
    private final TenantContextService tenantContextService;
    private final TenantRequestContextProvider contextProvider;

    public TenantController(TenantService tenantService,
                            TenantContextService tenantContextService,
                            TenantRequestContextProvider contextProvider) {
        this.tenantService = tenantService;
        this.tenantContextService = tenantContextService;
        this.contextProvider = contextProvider;
    }

    @GetMapping("/me")
    public ApiResponse<List<MyTenantVO>> listMyTenants(
            HttpServletRequest servletRequest) {
        TrustedRequestContext context =
                contextProvider.requireUserContext(servletRequest);
        return ApiResponse.success(
                tenantService.listMyTenants(context.getUserId()));
    }

    @GetMapping("/current")
    public ApiResponse<TenantContextVO> current(
            HttpServletRequest servletRequest) {
        TrustedRequestContext context =
                contextProvider.requireUserContext(servletRequest);
        TenantContextValidationRequest request = new TenantContextValidationRequest();
        request.setUserId(String.valueOf(context.getUserId()));
        request.setGlobalRole(context.getGlobalRole());
        request.setContextType(context.getContextType().name());
        request.setTenantId(context.getTenantId());
        request.setTenantCode(context.getTenantCode());
        request.setTenantRole(context.getTenantRole());
        request.setContextVersion(context.getTenantContextVersion());
        request.setMemberContextVersion(context.getMemberContextVersion());
        request.setAuthorities(context.getPlatformAuthorities());
        request.setWriteRequest(false);
        return ApiResponse.success(tenantContextService.validate(request).getContext());
    }
}
