package com.plagod.service;

import com.plagod.dto.entitlement.VerifiedPaymentCallback;
import com.plagod.vo.entitlement.PaymentCallbackResultVO;

public interface PaymentCallbackService {

    PaymentCallbackResultVO handleSuccess(VerifiedPaymentCallback callback);
}