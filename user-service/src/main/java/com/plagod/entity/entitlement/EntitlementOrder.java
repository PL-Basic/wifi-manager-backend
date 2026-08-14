package com.plagod.entity.entitlement;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_entitlement_order")
public class EntitlementOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "order_id", type = IdType.AUTO)
    private Long orderId;

    private Long tenantId;
    private String orderNo;
    private Long userId;
    private String clientRequestId;
    private String productCode;
    private String orderType;
    @TableField(
            insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private String sourceType;
    private String sourceReference;
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
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
