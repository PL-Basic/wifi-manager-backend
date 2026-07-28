package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.user.EntitlementLeaseRequest;
import com.plagod.vo.user.EntitlementLeaseResult;
import com.plagod.vo.user.EntitlementSnapshotVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "user-service")
public interface UserEntitlementClient {

    @PostMapping("/internal/entitlements/lease")
    ApiResponse<EntitlementLeaseResult> acquireLease(@RequestHeader("X-Internal-Token") String internalToken,
                                                     @RequestBody EntitlementLeaseRequest request);

    @GetMapping("/internal/entitlements/users/{userId}/snapshot")
    ApiResponse<EntitlementSnapshotVO> getSnapshot(@RequestHeader("X-Internal-Token") String internalToken,
                                                   @PathVariable("userId") Long userId,
                                                   @RequestParam("entitlementId") Long entitlementId);
}