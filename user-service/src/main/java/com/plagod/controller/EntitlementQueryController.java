package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.EntitlementQueryService;
import com.plagod.vo.entitlement.DurationPurchasePageResult;
import com.plagod.vo.entitlement.EntitlementUsagePageResult;
import com.plagod.vo.user.EntitlementSnapshotVO;
import com.plagod.utils.TenantScopeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/entitlements")
public class EntitlementQueryController {

    @Autowired
    private EntitlementQueryService queryService;

    @GetMapping("/me")
    public ApiResponse<EntitlementSnapshotVO> getOwnEntitlement(@RequestHeader("X-Tenant-Id") String tenantId,
                                                                @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(queryService.getByUserId(
                TenantScopeUtils.requireTenantId(tenantId), userId));
    }

    @GetMapping("/purchases")
    public ApiResponse<DurationPurchasePageResult> pagePurchases(@RequestHeader("X-Tenant-Id") String tenantId,
                                                                 @RequestHeader("X-User-Id") Long userId,
                                                                 @RequestParam(defaultValue = "1") Integer current,
                                                                 @RequestParam(defaultValue = "10") Integer size) {
        return ApiResponse.success(queryService.pagePurchases(
                TenantScopeUtils.requireTenantId(tenantId), userId, current, size));
    }

    @GetMapping("/usage-logs")
    public ApiResponse<EntitlementUsagePageResult> pageUsageLogs(@RequestHeader("X-Tenant-Id") String tenantId,
                                                                 @RequestHeader("X-User-Id") Long userId,
                                                                 @RequestParam(defaultValue = "1") Integer current,
                                                                 @RequestParam(defaultValue = "10") Integer size) {

        return ApiResponse.success(queryService.pageUsageLogs(
                TenantScopeUtils.requireTenantId(tenantId), userId, current, size));
    }
}
