package com.plagod.vo.entitlement;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RefundVO {

    private String refundNo;
    private String orderNo;
    private String paymentNo;
    private Long purchaseId;
    private Long userId;
    private String requestId;
    private String channel;
    private String status;
    private String reason;

    private Long requestedSeconds;
    private Long requestedAmountCents;
    private Long refundedSeconds;
    private Long refundAmountCents;

    private Long reviewerId;
    private String reviewerName;
    private String reviewComment;
    private LocalDateTime reviewTime;

    private String channelRefundNo;
    private String failureMessage;
    private LocalDateTime completeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}