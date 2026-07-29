package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.entitlement.LocalDemoPaymentCallbackRequest;
import com.plagod.dto.entitlement.VerifiedPaymentCallback;
import com.plagod.service.PaymentCallbackService;
import com.plagod.service.payment.LocalDemoPaymentChannelAdapter;
import com.plagod.vo.entitlement.PaymentCallbackResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/payment/callbacks")
public class PaymentCallbackController {

    @Autowired
    private LocalDemoPaymentChannelAdapter localDemoAdapter;

    @Autowired
    private PaymentCallbackService paymentCallbackService;

    @PostMapping("/local-demo")
    public ApiResponse<PaymentCallbackResultVO> localDemoCallback(@Valid @RequestBody LocalDemoPaymentCallbackRequest request) {

        VerifiedPaymentCallback callback = localDemoAdapter.verify(request);

        return ApiResponse.success("支付回调处理成功", paymentCallbackService.handleSuccess(callback));
    }
}