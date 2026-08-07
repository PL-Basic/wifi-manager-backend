package com.plagod.vo.entitlement;

import lombok.Data;

@Data
public class EntitlementProductVO {

    private String productCode;
    private String name;
    private String entitlementMode;
    private Integer pricingVersion;
    private Long grantSeconds;
    private Integer grantMonths;
    private Long amountCents;
    private Long referenceAmountCents;
    private Integer subscriptionRatioBps;
    private Integer periodDiscountBps;
    private Boolean customAmountAllowed;
    private Long minAmountCents;
    private Long maxAmountCents;
    private Long secondsPerCent;
}
