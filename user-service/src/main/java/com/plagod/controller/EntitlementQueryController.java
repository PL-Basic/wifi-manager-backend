package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.UserRequestContextPolicy;
import com.plagod.service.EntitlementQueryService;
import com.plagod.vo.entitlement.DurationPurchasePageResult;
import com.plagod.vo.entitlement.EntitlementUsagePageResult;
import com.plagod.vo.user.EntitlementSnapshotVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/entitlements")
public class EntitlementQueryController {

    @Autowired
    private EntitlementQueryService queryService;

    @Autowired
    private UserRequestContextPolicy contextPolicy;

    @GetMapping("/me")
    public ApiResponse<EntitlementSnapshotVO> getOwnEntitlement(
            HttpServletRequest request) {
        TrustedRequestContext context =
                contextPolicy.requireTenantBoundActor(request);
        return ApiResponse.success(queryService.getByUserId(
                contextPolicy.tenantId(context),
                context.getUserId()));
    }

    @GetMapping("/purchases")
    public ApiResponse<DurationPurchasePageResult> pagePurchases(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        TrustedRequestContext context =
                contextPolicy.requireTenantBoundActor(request);
        return ApiResponse.success(queryService.pagePurchases(
                contextPolicy.tenantId(context),
                context.getUserId(),
                current,
                size));
    }

    @GetMapping("/usage-logs")
    public ApiResponse<EntitlementUsagePageResult> pageUsageLogs(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        TrustedRequestContext context =
                contextPolicy.requireTenantBoundActor(request);
        return ApiResponse.success(queryService.pageUsageLogs(
                contextPolicy.tenantId(context),
                context.getUserId(),
                current,
                size));
    }
}
