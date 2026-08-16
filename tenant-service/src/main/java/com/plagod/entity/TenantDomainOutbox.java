package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_tenant_domain_outbox")
public class TenantDomainOutbox {

    @TableId(type = IdType.AUTO)
    private Long outboxId;
    private String eventId;
    private Long tenantId;
    private String aggregateType;
    private Long aggregateId;
    private Long contextVersion;
    private String eventType;
    private String outboxStatus;
    private Integer attemptCount;
    private LocalDateTime nextAttemptTime;
    private String workerId;
    private LocalDateTime leaseUntil;
    private LocalDateTime claimedTime;
    private LocalDateTime publishedTime;
    private String lastErrorCode;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
