package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.entitlement.EntitlementAdjustmentRequest;
import com.plagod.dto.entitlement.LocalDemoRefundResultRequest;
import com.plagod.dto.entitlement.RefundReviewRequest;
import com.plagod.dto.entitlement.VerifiedRefundResult;
import com.plagod.service.EntitlementAdjustmentService;
import com.plagod.service.EntitlementQueryService;
import com.plagod.service.RefundQueryService;
import com.plagod.service.RefundService;
import com.plagod.service.payment.LocalDemoRefundChannelAdapter;
import com.plagod.vo.entitlement.DurationPurchasePageResult;
import com.plagod.vo.entitlement.EntitlementUsagePageResult;
import com.plagod.vo.entitlement.RefundPageResult;
import com.plagod.vo.entitlement.RefundVO;
import com.plagod.vo.user.EntitlementSnapshotVO;
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
    private RefundService refundService;
    @Autowired
    private LocalDemoRefundChannelAdapter demoRefundAdapter;
    @Autowired
    private RefundQueryService refundQueryService;

    @GetMapping("/users/{userId}")
    public ApiResponse<EntitlementSnapshotVO> getEntitlement(@PathVariable Long userId) {
        return ApiResponse.success(queryService.getByUserId(userId));
    }

    @GetMapping("/users/{userId}/purchases")
    public ApiResponse<DurationPurchasePageResult> pagePurchases(@PathVariable Long userId,
                                                                 @RequestParam(defaultValue = "1") Long current, @RequestParam(defaultValue = "10") Long size) {

        return ApiResponse.success(queryService.pagePurchases(userId, current, size));
    }

    @GetMapping("/users/{userId}/usage-logs")
    public ApiResponse<EntitlementUsagePageResult> pageUsageLogs(@PathVariable Long userId,
                                                                 @RequestParam(defaultValue = "1") Long current,
                                                                 @RequestParam(defaultValue = "10") Long size) {

        return ApiResponse.success(queryService.pageUsageLogs(userId, current, size));
    }

    @PostMapping("/users/{userId}/adjustments")
    public ApiResponse<EntitlementSnapshotVO> adjust(@PathVariable Long userId,
                                                     @RequestHeader("X-User-Id") Long operatorId,
                                                     @RequestHeader("X-User-Name") String operatorName,
                                                     @Valid @RequestBody EntitlementAdjustmentRequest request) {

        return ApiResponse.success("权益调整完成", adjustmentService.adjust(userId, operatorId, operatorName, request));
    }

    @PutMapping("/refunds/{refundNo}/review")
    public ApiResponse<RefundVO> reviewRefund(@PathVariable String refundNo,
                                              @RequestHeader("X-User-Id") Long reviewerId,
                                              @RequestHeader("X-User-Name") String reviewerName,
                                              @Valid @RequestBody RefundReviewRequest request) {

        return ApiResponse.success("退款审核完成", refundService.review(refundNo, reviewerId, reviewerName, request));
    }

    @PostMapping("/refunds/{refundNo}/demo-result")
    public ApiResponse<RefundVO> completeDemoRefund(@PathVariable String refundNo,
                                                    @Valid @RequestBody LocalDemoRefundResultRequest request) {

        VerifiedRefundResult result = demoRefundAdapter.build(refundNo, request);

        return ApiResponse.success("Demo退款渠道结果处理完成", refundService.handleChannelResult(result));
    }

    @GetMapping("/refunds")
    public ApiResponse<RefundPageResult> pageRefunds(@RequestParam(defaultValue = "1") Long current,
                                                     @RequestParam(defaultValue = "10") Long size,
                                                     @RequestParam(required = false) Long userId,
                                                     @RequestParam(required = false) String status) {

        return ApiResponse.success(refundQueryService.pageForAdmin(current, size, userId, status));
    }
}