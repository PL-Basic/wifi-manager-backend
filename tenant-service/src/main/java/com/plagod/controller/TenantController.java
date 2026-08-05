package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.TenantService;
import com.plagod.vo.tenant.MyTenantVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/me")
    public ApiResponse<List<MyTenantVO>> listMyTenants(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(tenantService.listMyTenants(userId));
    }
}
