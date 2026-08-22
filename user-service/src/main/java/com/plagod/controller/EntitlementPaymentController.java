package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.entitlement.PaymentCreateRequest;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.UserRequestContextPolicy;
import com.plagod.service.PaymentService;
import com.plagod.vo.entitlement.PaymentCallbackResultVO;
import com.plagod.vo.entitlement.PaymentVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/entitlements")
public class EntitlementPaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserRequestContextPolicy contextPolicy;

    @PostMapping("/orders/{orderNo}/payments")
    public ApiResponse<PaymentVO> createPayment(
            HttpServletRequest servletRequest,
            @PathVariable String orderNo,
            @Valid @RequestBody PaymentCreateRequest request) {
        TrustedRequestContext context =
                contextPolicy.requireTenantBoundActor(servletRequest);
        return ApiResponse.success("支付记录创建成功", paymentService.createPayment(
                contextPolicy.tenantId(context),
                context.getUserId(),
                orderNo,
                request));
    }

    @GetMapping("/payments/{paymentNo}")
    public ApiResponse<PaymentVO> getPayment(
            HttpServletRequest request,
            @PathVariable String paymentNo) {
        TrustedRequestContext context =
                contextPolicy.requireTenantBoundActor(request);
        return ApiResponse.success(paymentService.getOwnPayment(
                contextPolicy.tenantId(context),
                context.getUserId(),
                paymentNo));
    }

    @PostMapping("/payments/{paymentNo}/demo-complete")
    public ApiResponse<PaymentCallbackResultVO> completeDemoPayment(
            HttpServletRequest request,
            @PathVariable String paymentNo) {
        TrustedRequestContext context =
                contextPolicy.requireTenantBoundActor(request);
        return ApiResponse.success("Demo 支付完成", paymentService.completeLocalDemo(
                contextPolicy.tenantId(context),
                context.getUserId(),
                paymentNo));
    }
}
