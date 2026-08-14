package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_market_order")
public class MarketplaceOrder {

    @TableId(type = IdType.AUTO)
    private Long orderId;
    private String orderNo;
    private Long tenantId;
    private String subjectType;
    private Long userId;
    @TableField(
            insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private String subjectKey;
    private Long actorUserId;
    private String clientRequestId;
    private String requestFingerprint;
    private Long totalAmountCents;
    private String paymentMode;
    private String paymentStatus;
    private String orderStatus;
    private LocalDateTime paidTime;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
