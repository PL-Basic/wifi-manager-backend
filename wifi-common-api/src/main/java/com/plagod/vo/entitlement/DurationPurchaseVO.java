package com.plagod.vo.entitlement;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DurationPurchaseVO {
    private String purchaseId;
    private String orderNo;
    private Long purchasedSeconds;
    private Long remainingSeconds;
    private Long paidAmountCents;
    private Integer refundable;
    private Integer status;
    private Long refundedAmountCents;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;
}
