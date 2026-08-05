package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.TenantContextResolveRequest;
import com.plagod.vo.tenant.TenantContextVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "tenant-service", contextId = "tenantContextClient")
public interface TenantContextClient {

    @PostMapping("/internal/tenants/context/resolve")
    ApiResponse<TenantContextVO> resolve(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody TenantContextResolveRequest request);
}
