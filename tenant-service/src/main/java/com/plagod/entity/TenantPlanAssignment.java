package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_tenant_plan_assignment")
public class TenantPlanAssignment {
    @TableId(type = IdType.AUTO)
    private Long assignmentId;
    private Long tenantId;
    private Long planVersionId;
    private String clientRequestId;
    private String sourceType;
    private String sourceReference;
    private String requestFingerprint;
    private LocalDateTime requestedStartTime;
    private LocalDateTime requestedEndTime;
    private String planSnapshotHash;
    private String status;
    private Long subscriptionId;
    private Long actorUserId;
    private String reasonCode;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
