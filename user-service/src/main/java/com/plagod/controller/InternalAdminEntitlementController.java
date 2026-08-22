package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.entitlement.EntitlementAdjustmentRequest;
import com.plagod.dto.entitlement.EntitlementRewardOrderRequest;
import com.plagod.dto.entitlement.LocalDemoRefundResultRequest;
import com.plagod.dto.entitlement.RefundReviewRequest;
import com.plagod.dto.entitlement.UnlimitedEntitlementRequest;
import com.plagod.dto.entitlement.VerifiedRefundResult;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.UserRequestContextPolicy;
import com.plagod.service.EntitlementAdjustmentService;
import com.plagod.service.EntitlementRewardOrderService;
import com.plagod.service.EntitlementQueryService;
import com.plagod.service.RefundQueryService;
import com.plagod.service.RefundService;
import com.plagod.service.UserManageService;
import com.plagod.service.payment.LocalDemoRefundChannelAdapter;
import com.plagod.vo.entitlement.DurationPurchasePageResult;
import com.plagod.vo.entitlement.EntitlementOrderVO;
import com.plagod.vo.entitlement.EntitlementUsagePageResult;
import com.plagod.vo.entitlement.RefundPageResult;
import com.plagod.vo.entitlement.RefundVO;
import com.plagod.vo.user.EntitlementSnapshotVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
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
    @Autowired
    private UserManageService userManageService;
    @Autowired
    private UserRequestContextPolicy contextPolicy;

    @GetMapping("/users/{userId}")
    public ApiResponse<EntitlementSnapshotVO> getEntitlement(
            HttpServletRequest request,
            @PathVariable Long userId) {
        TrustedRequestContext context =
                contextPolicy.requireEntitlementAdminActor(request);
        return ApiResponse.success(queryService.getByUserId(
                contextPolicy.tenantId(context),
                userId));
    }

    @GetMapping("/users/{userId}/purchases")
    public ApiResponse<DurationPurchasePageResult> pagePurchases(
            HttpServletRequest request,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        TrustedRequestContext context =
                contextPolicy.requireEntitlementAdminActor(request);
        return ApiResponse.success(queryService.pagePurchases(
                contextPolicy.tenantId(context),
                userId,
                current,
                size));
    }

    @GetMapping("/users/{userId}/usage-logs")
    public ApiResponse<EntitlementUsagePageResult> pageUsageLogs(
            HttpServletRequest request,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        TrustedRequestContext context =
                contextPolicy.requireEntitlementAdminActor(request);
        return ApiResponse.success(queryService.pageUsageLogs(
                contextPolicy.tenantId(context),
                userId,
                current,
                size));
    }

    @PostMapping("/users/{userId}/adjustments")
    public ApiResponse<EntitlementSnapshotVO> adjust(@PathVariable Long userId,
                                                     HttpServletRequest servletRequest,
                                                     @Valid @RequestBody EntitlementAdjustmentRequest request) {
        TrustedRequestContext context =
                contextPolicy.requireEntitlementAdminActor(servletRequest);
        return ApiResponse.success("权益调整完成", adjustmentService.adjust(
                contextPolicy.tenantId(context),
                userId,
                context.getUserId(),
                actorName(context),
                request));
    }

    @PostMapping("/users/{userId}/unlimited-adjustments")
    public ApiResponse<EntitlementSnapshotVO> adjustUnlimited(
            @PathVariable Long userId,
            HttpServletRequest servletRequest,
            @Valid @RequestBody UnlimitedEntitlementRequest request) {
        TrustedRequestContext context =
                contextPolicy.requirePlatformTenantSuperAdmin(servletRequest);
        return ApiResponse.success("无限权益调整完成", adjustmentService.adjustUnlimited(
                contextPolicy.tenantId(context),
                userId,
                context.getUserId(),
                actorName(context),
                context.getGlobalRole(),
                request));
    }

    @PostMapping("/users/{userId}/reward-orders")
    public ApiResponse<EntitlementOrderVO> createRewardOrder(
            @PathVariable Long userId,
            HttpServletRequest servletRequest,
            @Valid @RequestBody EntitlementRewardOrderRequest request) {
        TrustedRequestContext context =
                contextPolicy.requirePlatformTenantSuperAdmin(servletRequest);
        return ApiResponse.success(
                "奖励订单创建并生效",
                rewardOrderService.create(
                        contextPolicy.tenantId(context),
                        userId,
                        context.getUserId(),
                        actorName(context),
                        request)
        );
    }

    @PutMapping("/refunds/{refundNo}/review")
    public ApiResponse<RefundVO> reviewRefund(@PathVariable String refundNo,
                                              HttpServletRequest servletRequest,
                                              @Valid @RequestBody RefundReviewRequest request) {
        TrustedRequestContext context =
                contextPolicy.requireEntitlementAdminActor(servletRequest);
        return ApiResponse.success("退款审核完成", refundService.review(
                contextPolicy.tenantId(context),
                refundNo,
                context.getUserId(),
                actorName(context),
                request));
    }

    @PostMapping("/refunds/{refundNo}/demo-result")
    public ApiResponse<RefundVO> completeDemoRefund(@PathVariable String refundNo,
                                                    HttpServletRequest servletRequest,
                                                    @Valid @RequestBody LocalDemoRefundResultRequest request) {
        TrustedRequestContext context =
                contextPolicy.requireEntitlementAdminActor(servletRequest);
        refundQueryService.getForAdmin(
                contextPolicy.tenantId(context),
                refundNo);
        VerifiedRefundResult result = demoRefundAdapter.build(refundNo, request);

        return ApiResponse.success("Demo退款渠道结果处理完成", refundService.handleChannelResult(result));
    }

    @GetMapping("/refunds")
    public ApiResponse<RefundPageResult> pageRefunds(
                                                     HttpServletRequest request,
                                                     @RequestParam(defaultValue = "1") Integer current,
                                                     @RequestParam(defaultValue = "10") Integer size,
                                                     @RequestParam(required = false) Long userId,
                                                     @RequestParam(required = false) String status) {
        TrustedRequestContext context =
                contextPolicy.requireEntitlementAdminActor(request);
        return ApiResponse.success(refundQueryService.pageForAdmin(
                contextPolicy.tenantId(context),
                current,
                size,
                userId,
                status));
    }

    @GetMapping("/refunds/{refundNo}")
    public ApiResponse<RefundVO> getRefund(
            HttpServletRequest request,
            @PathVariable String refundNo) {
        TrustedRequestContext context =
                contextPolicy.requireEntitlementAdminActor(request);
        return ApiResponse.success(refundQueryService.getForAdmin(
                contextPolicy.tenantId(context),
                refundNo));
    }

    private String actorName(TrustedRequestContext context) {
        return userManageService.getUser(
                context.getUserId()).getUsername();
    }
}
