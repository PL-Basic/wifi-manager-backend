package com.plagod.vo.entitlement;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EntitlementOrderVO {

    private String orderNo;
    private String tenantId;
    private Long userId;
    private String productCode;
    private String orderType;
    private String entitlementMode;
    private Integer pricingVersion;
    private Long grantSeconds;
    private Integer grantMonths;
    private Long amountCents;
    private Long referenceAmountCents;
    private Integer subscriptionRatioBps;
    private Integer periodDiscountBps;
    private Long paidAmountCents;
    private Long refundedAmountCents;
    private String status;
    private LocalDateTime expireTime;
    private LocalDateTime paidTime;
    private LocalDateTime fulfilledTime;
    private LocalDateTime closeTime;
    private String closeReason;
    private String remark;
    private LocalDateTime createTime;
}
