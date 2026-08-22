package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.user.EntitlementLeaseRequest;
import com.plagod.vo.user.EntitlementLeaseResult;
import com.plagod.vo.user.EntitlementSnapshotVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-service")
public interface UserEntitlementClient {

    @PostMapping("/internal/entitlements/lease")
    ApiResponse<EntitlementLeaseResult> acquireLease(
            @RequestBody EntitlementLeaseRequest request);

    @GetMapping("/internal/entitlements/users/{userId}/snapshot")
    ApiResponse<EntitlementSnapshotVO> getSnapshot(
            @PathVariable("userId") Long userId,
            @RequestParam("entitlementId") Long entitlementId);
}
