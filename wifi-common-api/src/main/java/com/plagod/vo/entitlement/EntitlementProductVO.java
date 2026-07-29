package com.plagod.vo.entitlement;

import lombok.Data;

@Data
public class EntitlementProductVO {

    private String productCode;
    private String name;
    private String entitlementMode;
    private Long grantSeconds;
    private Long amountCents;
}