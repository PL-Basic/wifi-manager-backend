package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.TenantService;
import com.plagod.service.TenantContextService;
import com.plagod.dto.tenant.TenantContextValidationRequest;
import com.plagod.vo.tenant.MyTenantVO;
import com.plagod.vo.tenant.TenantContextVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tenants")
public class TenantController {

    private final TenantService tenantService;
    private final TenantContextService tenantContextService;

    public TenantController(TenantService tenantService,
                            TenantContextService tenantContextService) {
        this.tenantService = tenantService;
        this.tenantContextService = tenantContextService;
    }

    @GetMapping("/me")
    public ApiResponse<List<MyTenantVO>> listMyTenants(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(tenantService.listMyTenants(userId));
    }

    @GetMapping("/current")
    public ApiResponse<TenantContextVO> current(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") Integer role,
            @RequestHeader("X-Context-Type") String contextType,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-Tenant-Code", required = false) String tenantCode,
            @RequestHeader(value = "X-Tenant-Role", required = false) String tenantRole,
            @RequestHeader(value = "X-Tenant-Context-Version", required = false) Long contextVersion,
            @RequestHeader(value = "X-Member-Context-Version", required = false) Long memberContextVersion,
            @RequestHeader(value = "X-Platform-Authorities", required = false) String authorities) {
        TenantContextValidationRequest request = new TenantContextValidationRequest();
        request.setUserId(String.valueOf(userId));
        request.setGlobalRole(role);
        request.setContextType(contextType);
        request.setTenantId(tenantId);
        request.setTenantCode(tenantCode);
        request.setTenantRole(tenantRole);
        request.setContextVersion(contextVersion);
        request.setMemberContextVersion(memberContextVersion);
        request.setAuthorities(parseAuthorities(authorities));
        request.setWriteRequest(false);
        return ApiResponse.success(tenantContextService.validate(request).getContext());
    }

    private List<String> parseAuthorities(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }
}
