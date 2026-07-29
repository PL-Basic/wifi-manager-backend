package com.plagod.vo.entitlement;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentCallbackResultVO {

    private String paymentNo;
    private String orderNo;
    private String paymentStatus;
    private String orderStatus;
    private boolean duplicate;

    private Long entitlementId;
    private String entitlementMode;
    private Long grantedSeconds;
    private Long remainingSeconds;
    private LocalDateTime subscriptionEndTime;
}