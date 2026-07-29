package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.EntitlementQueryService;
import com.plagod.vo.entitlement.DurationPurchasePageResult;
import com.plagod.vo.entitlement.EntitlementUsagePageResult;
import com.plagod.vo.user.EntitlementSnapshotVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/entitlements")
public class EntitlementQueryController {

    @Autowired
    private EntitlementQueryService queryService;

    @GetMapping("/me")
    public ApiResponse<EntitlementSnapshotVO> getOwnEntitlement(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(queryService.getByUserId(userId));
    }

    @GetMapping("/purchases")
    public ApiResponse<DurationPurchasePageResult> pagePurchases(@RequestHeader("X-User-Id") Long userId,
                                                                 @RequestParam(defaultValue = "1") Long current,
                                                                 @RequestParam(defaultValue = "10") Long size) {
        return ApiResponse.success(queryService.pagePurchases(userId, current, size));
    }

    @GetMapping("/usage-logs")
    public ApiResponse<EntitlementUsagePageResult> pageUsageLogs(@RequestHeader("X-User-Id") Long userId,
                                                                 @RequestParam(defaultValue = "1") Long current,
                                                                 @RequestParam(defaultValue = "10") Long size) {

        return ApiResponse.success(queryService.pageUsageLogs(userId, current, size));
    }
}