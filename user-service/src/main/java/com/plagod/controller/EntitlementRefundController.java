package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.entitlement.RefundApplyRequest;
import com.plagod.service.RefundQueryService;
import com.plagod.service.RefundService;
import com.plagod.vo.entitlement.RefundPageResult;
import com.plagod.vo.entitlement.RefundVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/entitlements/refunds")
public class EntitlementRefundController {

    @Autowired
    private RefundService refundService;

    @Autowired
    private RefundQueryService refundQueryService;

    @PostMapping
    public ApiResponse<RefundVO> apply(@RequestHeader("X-User-Id") Long userId,
                                       @Valid @RequestBody RefundApplyRequest request) {

        return ApiResponse.success("退款申请已提交，剩余时长已冻结", refundService.apply(userId, request));
    }

    @GetMapping
    public ApiResponse<RefundPageResult> pageOwnRefunds(@RequestHeader("X-User-Id") Long userId,
                                                        @RequestParam(defaultValue = "1") Long current,
                                                        @RequestParam(defaultValue = "10") Long size,
                                                        @RequestParam(required = false) String status) {

        return ApiResponse.success(refundQueryService.pageOwnRefunds(userId, current, size, status));
    }

    @GetMapping("/{refundNo}")
    public ApiResponse<RefundVO> getOwnRefund(@RequestHeader("X-User-Id") Long userId,
                                              @PathVariable String refundNo) {

        return ApiResponse.success(refundQueryService.getOwnRefund(userId, refundNo));
    }
}