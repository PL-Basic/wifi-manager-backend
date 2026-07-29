package com.plagod.entity.entitlement;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_refund_record")
public class RefundRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "refund_id", type = IdType.AUTO)
    private Long refundId;

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
    private String channelEventId;
    private String channelPayloadHash;
    private String failureMessage;
    private LocalDateTime completeTime;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}