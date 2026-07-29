package com.plagod.entity.entitlement;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_payment_record")
public class PaymentRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "payment_id", type = IdType.AUTO)
    private Long paymentId;

    private String paymentNo;
    private String orderNo;
    private Long userId;
    private String requestId;
    private String businessKey;
    private String channel;
    private Long amountCents;
    private Long paidAmountCents;
    private Long refundedAmountCents;
    private String status;
    private String channelTransactionNo;
    private String callbackEventId;
    private String callbackPayloadHash;
    private LocalDateTime paidTime;
    private String failureCode;
    private String failureMessage;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}