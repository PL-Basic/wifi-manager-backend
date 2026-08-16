package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_market_fulfillment")
public class MarketplaceFulfillment {

    @TableId(type = IdType.AUTO)
    private Long fulfillmentId;
    private Long tenantId;
    private Long orderId;
    private Long orderItemId;
    private String eventKey;
    private String fulfillmentType;
    private String fulfillmentMode;
    private String targetBusinessKey;
    private String status;
    private String resultReference;
    private Integer attemptCount;
    private LocalDateTime nextAttemptTime;
    private LocalDateTime leaseUntil;
    private String workerId;
    private String lastErrorKey;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
