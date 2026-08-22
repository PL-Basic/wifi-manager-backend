package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.entitlement.RefundApplyRequest;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.UserRequestContextPolicy;
import com.plagod.service.RefundQueryService;
import com.plagod.service.RefundService;
import com.plagod.vo.entitlement.RefundPageResult;
import com.plagod.vo.entitlement.RefundVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/entitlements/refunds")
public class EntitlementRefundController {

    @Autowired
    private RefundService refundService;

    @Autowired
    private RefundQueryService refundQueryService;

    @Autowired
    private UserRequestContextPolicy contextPolicy;

    @PostMapping
    public ApiResponse<RefundVO> apply(
            HttpServletRequest servletRequest,
            @Valid @RequestBody RefundApplyRequest request) {
        TrustedRequestContext context =
                contextPolicy.requireTenantBoundActor(servletRequest);
        return ApiResponse.success("退款申请已提交，剩余时长已冻结", refundService.apply(
                contextPolicy.tenantId(context),
                context.getUserId(),
                request));
    }

    @GetMapping
    public ApiResponse<RefundPageResult> pageOwnRefunds(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String status) {
        TrustedRequestContext context =
                contextPolicy.requireTenantBoundActor(request);
        return ApiResponse.success(refundQueryService.pageOwnRefunds(
                contextPolicy.tenantId(context),
                context.getUserId(),
                current,
                size,
                status));
    }

    @GetMapping("/{refundNo}")
    public ApiResponse<RefundVO> getOwnRefund(
            HttpServletRequest request,
            @PathVariable String refundNo) {
        TrustedRequestContext context =
                contextPolicy.requireTenantBoundActor(request);
        return ApiResponse.success(refundQueryService.getOwnRefund(
                contextPolicy.tenantId(context),
                context.getUserId(),
                refundNo));
    }
}
