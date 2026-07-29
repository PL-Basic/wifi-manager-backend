package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.entitlement.PaymentCreateRequest;
import com.plagod.service.PaymentService;
import com.plagod.vo.entitlement.PaymentCallbackResultVO;
import com.plagod.vo.entitlement.PaymentVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/entitlements")
public class EntitlementPaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/orders/{orderNo}/payments")
    public ApiResponse<PaymentVO> createPayment(@RequestHeader("X-User-Id") Long userId,
                                                @PathVariable String orderNo,
                                                @Valid @RequestBody PaymentCreateRequest request) {

        return ApiResponse.success("支付记录创建成功", paymentService.createPayment(userId, orderNo, request));
    }

    @GetMapping("/payments/{paymentNo}")
    public ApiResponse<PaymentVO> getPayment(@RequestHeader("X-User-Id") Long userId,
                                             @PathVariable String paymentNo) {

        return ApiResponse.success(paymentService.getOwnPayment(userId, paymentNo));
    }

    @PostMapping("/payments/{paymentNo}/demo-complete")
    public ApiResponse<PaymentCallbackResultVO> completeDemoPayment(@RequestHeader("X-User-Id") Long userId,
                                                                    @PathVariable String paymentNo) {

        return ApiResponse.success("Demo 支付完成", paymentService.completeLocalDemo(userId, paymentNo));
    }
}