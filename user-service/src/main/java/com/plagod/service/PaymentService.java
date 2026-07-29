package com.plagod.service;

import com.plagod.dto.entitlement.PaymentCreateRequest;
import com.plagod.vo.entitlement.PaymentCallbackResultVO;
import com.plagod.vo.entitlement.PaymentVO;

public interface PaymentService {

    PaymentVO createPayment(Long userId, String orderNo, PaymentCreateRequest request);

    PaymentVO getOwnPayment(Long userId, String paymentNo);

    PaymentCallbackResultVO completeLocalDemo(Long userId, String paymentNo);
}