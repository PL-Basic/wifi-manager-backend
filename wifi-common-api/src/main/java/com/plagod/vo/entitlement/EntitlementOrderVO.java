package com.plagod.vo.entitlement;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EntitlementOrderVO {

    private String orderNo;
    private Long userId;
    private String productCode;
    private String entitlementMode;
    private Long grantSeconds;
    private Long amountCents;
    private Long paidAmountCents;
    private Long refundedAmountCents;
    private String status;
    private LocalDateTime expireTime;
    private LocalDateTime paidTime;
    private LocalDateTime fulfilledTime;
    private LocalDateTime closeTime;
    private String closeReason;
    private LocalDateTime createTime;
}