package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_tenant_subscription")
public class TenantSubscription {
    @TableId(type = IdType.AUTO)
    private Long subscriptionId;
    private Long tenantId;
    private Long planVersionId;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String source;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
