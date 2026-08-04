package com.plagod.vo.tenant;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantVO {
    private String tenantId;
    private String tenantCode;
    private String name;
    private String status;
    private String timezone;
    private String ownerUserId;
    private Long contextVersion;
    private Integer version;
    private Long memberCount;
    private String subscriptionStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
