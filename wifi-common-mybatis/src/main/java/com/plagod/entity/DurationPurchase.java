package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_duration_purchase")
public class DurationPurchase implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "purchase_id", type = IdType.AUTO)
    private Long purchaseId;

    // 支付或充值系统生成的唯一订单号
    private String orderNo;

    private Long userId;

    // 原始购买秒数，用于计算退款比例
    private Long purchasedSeconds;

    // 当前还能使用或退款的秒数
    private Long remainingSeconds;

    // 实际支付金额，统一使用整数分
    private Long paidAmountCents;

    // 1 表示允许退款，0 表示不可退款
    private Integer refundable;

    // 1-可用，2-耗尽，3-已退款
    private Integer status;

    private Long refundedAmountCents;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}