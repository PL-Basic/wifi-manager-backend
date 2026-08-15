package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.entitlement.EntitlementAdjustmentRequest;
import com.plagod.dto.entitlement.EntitlementRewardOrderRequest;
import com.plagod.dto.entitlement.LocalDemoRefundResultRequest;
import com.plagod.dto.entitlement.RefundReviewRequest;
import com.plagod.dto.entitlement.UnlimitedEntitlementRequest;
import com.plagod.dto.entitlement.VerifiedRefundResult;
import com.plagod.exception.ApiStatusException;
import com.plagod.service.EntitlementAdjustmentService;
import com.plagod.service.EntitlementRewardOrderService;
import com.plagod.service.EntitlementQueryService;
import com.plagod.service.RefundQueryService;
import com.plagod.service.RefundService;
import com.plagod.service.payment.LocalDemoRefundChannelAdapter;
import com.plagod.vo.entitlement.DurationPurchasePageResult;
import com.plagod.vo.entitlement.EntitlementOrderVO;
import com.plagod.vo.entitlement.EntitlementUsagePageResult;
import com.plagod.vo.entitlement.RefundPageResult;
import com.plagod.vo.entitlement.RefundVO;
import com.plagod.vo.user.EntitlementSnapshotVO;
import com.plagod.utils.TenantScopeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/internal/admin/entitlements")
public class InternalAdminEntitlementController {

    @Autowired
    private EntitlementQueryService queryService;
    @Autowired
    private EntitlementAdjustmentService adjustmentService;
    @Autowired
    private EntitlementRewardOrderService rewardOrderService;
    @Autowired
    private RefundService refundService;
    @Autowired
    private LocalDemoRefundChannelAdapter demoRefundAdapter;
    @Autowired
    private RefundQueryService refundQueryService;

    @GetMapping("/users/{userId}")
    public ApiResponse<EntitlementSnapshotVO> getEntitlement(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long userId) {
        return ApiResponse.success(queryService.getByUserId(
                TenantScopeUtils.requireTenantId(tenantId), userId));
    }

    @GetMapping("/users/{userId}/purchases")
    public ApiResponse<DurationPurchasePageResult> pagePurchases(
                                                                 @RequestHeader("X-Tenant-Id") String tenantId,
                                                                 @PathVariable Long userId,
                                                                 @RequestParam(defaultValue = "1") Integer current, @RequestParam(defaultValue = "10") Integer size) {

        return ApiResponse.success(queryService.pagePurchases(
                TenantScopeUtils.requireTenantId(tenantId), userId, current, size));
    }

    @GetMapping("/users/{userId}/usage-logs")
    public ApiResponse<EntitlementUsagePageResult> pageUsageLogs(
                                                                 @RequestHeader("X-Tenant-Id") String tenantId,
                                                                 @PathVariable Long userId,
                                                                 @RequestParam(defaultValue = "1") Integer current,
                                                                 @RequestParam(defaultValue = "10") Integer size) {

        return ApiResponse.success(queryService.pageUsageLogs(
                TenantScopeUtils.requireTenantId(tenantId), userId, current, size));
    }

    @PostMapping("/users/{userId}/adjustments")
    public ApiResponse<EntitlementSnapshotVO> adjust(@PathVariable Long userId,
                                                     @RequestHeader("X-Tenant-Id") String tenantId,
                                                     @RequestHeader("X-User-Id") Long operatorId,
                                                     @RequestHeader("X-User-Name") String operatorName,
                                                     @Valid @RequestBody EntitlementAdjustmentRequest request) {

        return ApiResponse.success("权益调整完成", adjustmentService.adjust(
                TenantScopeUtils.requireTenantId(tenantId), userId, operatorId, operatorName, request));
    }

    @PostMapping("/users/{userId}/unlimited-adjustments")
    public ApiResponse<EntitlementSnapshotVO> adjustUnlimited(
            @PathVariable Long userId,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id") Long operatorId,
            @RequestHeader("X-User-Name") String operatorName,
            @RequestHeader("X-User-Role") Integer operatorRole,
            @Valid @RequestBody UnlimitedEntitlementRequest request) {

        if (!Integer.valueOf(0).equals(operatorRole)) {
            throw ApiStatusException.forbidden("仅超级管理员可以授予或撤销无限权益");
        }

        return ApiResponse.success("无限权益调整完成", adjustmentService.adjustUnlimited(
                TenantScopeUtils.requireTenantId(tenantId), userId, operatorId,
                operatorName, operatorRole, request));
    }

    @PostMapping("/users/{userId}/reward-orders")
    public ApiResponse<EntitlementOrderVO> createRewardOrder(
            @PathVariable Long userId,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id") Long operatorId,
            @RequestHeader("X-User-Name") String operatorName,
            @RequestHeader("X-User-Role") Integer operatorRole,
            @Valid @RequestBody EntitlementRewardOrderRequest request) {

        if (!Integer.valueOf(0).equals(operatorRole)) {
            throw ApiStatusException.forbidden("仅超级管理员可以创建奖励订单");
        }

        return ApiResponse.success(
                "奖励订单创建并生效",
                rewardOrderService.create(TenantScopeUtils.requireTenantId(tenantId),
                        userId, operatorId, operatorName, request)
        );
    }

    @PutMapping("/refunds/{refundNo}/review")
    public ApiResponse<RefundVO> reviewRefund(@PathVariable String refundNo,
                                              @RequestHeader("X-Tenant-Id") String tenantId,
                                              @RequestHeader("X-User-Id") Long reviewerId,
                                              @RequestHeader("X-User-Name") String reviewerName,
                                              @Valid @RequestBody RefundReviewRequest request) {

        return ApiResponse.success("退款审核完成", refundService.review(
                TenantScopeUtils.requireTenantId(tenantId),
                refundNo, reviewerId, reviewerName, request));
    }

    @PostMapping("/refunds/{refundNo}/demo-result")
    public ApiResponse<RefundVO> completeDemoRefund(@PathVariable String refundNo,
                                                    @RequestHeader("X-Tenant-Id") String tenantId,
                                                    @Valid @RequestBody LocalDemoRefundResultRequest request) {

        refundQueryService.getForAdmin(TenantScopeUtils.requireTenantId(tenantId), refundNo);
        VerifiedRefundResult result = demoRefundAdapter.build(refundNo, request);

        return ApiResponse.success("Demo退款渠道结果处理完成", refundService.handleChannelResult(result));
    }

    @GetMapping("/refunds")
    public ApiResponse<RefundPageResult> pageRefunds(@RequestParam(defaultValue = "1") Integer current,
                                                     @RequestHeader("X-Tenant-Id") String tenantId,
                                                     @RequestParam(defaultValue = "10") Integer size,
                                                     @RequestParam(required = false) Long userId,
                                                     @RequestParam(required = false) String status) {

        return ApiResponse.success(refundQueryService.pageForAdmin(
                TenantScopeUtils.requireTenantId(tenantId), current, size, userId, status));
    }

    @GetMapping("/refunds/{refundNo}")
    public ApiResponse<RefundVO> getRefund(@RequestHeader("X-Tenant-Id") String tenantId,
                                          @PathVariable String refundNo) {
        return ApiResponse.success(refundQueryService.getForAdmin(
                TenantScopeUtils.requireTenantId(tenantId), refundNo));
    }
}
