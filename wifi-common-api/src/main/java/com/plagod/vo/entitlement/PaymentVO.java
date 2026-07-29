package com.plagod.vo.entitlement;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentVO {

    private String paymentNo;
    private String orderNo;
    private String channel;
    private Long amountCents;
    private Long paidAmountCents;
    private Long refundedAmountCents;
    private String status;
    private String businessKey;
    private String channelTransactionNo;
    private LocalDateTime paidTime;
    private LocalDateTime createTime;

    // 当前支付渠道要求客户端执行的下一步。
    private String actionType;
    private String actionValue;
}