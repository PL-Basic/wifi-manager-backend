package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.user.EntitlementLeaseRequest;
import com.plagod.service.EntitlementLeaseService;
import com.plagod.service.EntitlementQueryService;
import com.plagod.vo.user.EntitlementLeaseResult;
import com.plagod.vo.user.EntitlementSnapshotVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/internal/entitlements")
public class InternalEntitlementController {

    @Autowired
    private EntitlementLeaseService entitlementLeaseService;

    @Autowired
    private EntitlementQueryService entitlementQueryService;

    @PostMapping("/lease")
    public ApiResponse<EntitlementLeaseResult> acquireLease(@Valid @RequestBody EntitlementLeaseRequest request) {
        return ApiResponse.success(entitlementLeaseService.acquireLease(request));
    }

    @GetMapping("/users/{userId}/snapshot")
    public ApiResponse<EntitlementSnapshotVO> getSnapshot(@PathVariable("userId") Long userId, @RequestParam("entitlementId") Long entitlementId) {
        return ApiResponse.success(entitlementQueryService.getSnapshot(userId, entitlementId));
    }
}