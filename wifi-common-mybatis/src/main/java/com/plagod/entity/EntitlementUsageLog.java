package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_entitlement_usage_log")
public class EntitlementUsageLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long entitlementId;
    private Long userId;
    // 同一次消费重试必须复用同一个 requestId
    private String requestId;
    // 同一次请求可能跨多个购买批次，lineNo 区分流水明细
    private Integer lineNo;
    // 本行实际扣减的购买订单；订阅授权时可以为空
    private Long purchaseId;
    private String authorizationMode;
    private Long sessionId;
    private Long changeSeconds;
    private Long beforeSeconds;
    private Long afterSeconds;
    private String reason;
    private LocalDateTime createTime;
}