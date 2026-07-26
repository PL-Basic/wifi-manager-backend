package com.plagod.client;


import com.plagod.dto.ApiResponse;
import com.plagod.dto.user.EntitlementLeaseRequest;
import com.plagod.vo.user.EntitlementLeaseResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "user-service")
public interface UserEntitlementClient {

    @PostMapping("/internal/entitlements/lease")
    ApiResponse<EntitlementLeaseResult> acquireLease(@RequestHeader("X-Internal-Token") String internalToken, @RequestBody EntitlementLeaseRequest request);
}