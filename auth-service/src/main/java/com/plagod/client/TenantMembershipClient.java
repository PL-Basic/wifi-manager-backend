package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.DefaultTenantMembershipRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "tenant-service", contextId = "tenantMembershipClient")
public interface TenantMembershipClient {

    @PostMapping("/internal/tenants/default-memberships")
    ApiResponse<Void> ensureDefaultMembership(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody DefaultTenantMembershipRequest request);
}
