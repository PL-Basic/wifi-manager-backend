package com.plagod.controller;

import com.plagod.client.UserServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.entitlement.LocalDemoRefundResultRequest;
import com.plagod.dto.entitlement.RefundReviewRequest;
import com.plagod.vo.entitlement.RefundPageResult;
import com.plagod.vo.entitlement.RefundVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/admin/entitlements")
public class AdminEntitlementController {

    @Autowired
    private UserServiceClient userServiceClient;

    @PutMapping("/refunds/{refundNo}/review")
    public ApiResponse<RefundVO> reviewRefund(@PathVariable String refundNo,
                                              @RequestHeader("X-User-Id") Long reviewerId,
                                              @RequestHeader("X-User-Name") String reviewerName,
                                              @Valid @RequestBody RefundReviewRequest request) {

        return userServiceClient.reviewRefund(refundNo, reviewerId, reviewerName, request);
    }

    @PostMapping("/refunds/{refundNo}/demo-result")
    public ApiResponse<RefundVO> completeDemoRefund(@PathVariable String refundNo,
                                                    @Valid @RequestBody LocalDemoRefundResultRequest request) {

        return userServiceClient.completeDemoRefund(refundNo, request);
    }

    @GetMapping("/refunds")
    public ApiResponse<RefundPageResult> pageRefunds(@RequestParam(defaultValue = "1") Long current,
                                                     @RequestParam(defaultValue = "10") Long size,
                                                     @RequestParam(required = false) Long userId,
                                                     @RequestParam(required = false) String status) {

        return userServiceClient.pageRefunds(current, size, userId, status);
    }
}