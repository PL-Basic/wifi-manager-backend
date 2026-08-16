package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_ai_review_task")
public class AiReviewTask {

    @TableId(type = IdType.AUTO)
    private Long reviewTaskId;
    private String reviewRequestId;
    private String scene;
    private String businessType;
    private Long businessId;
    private Long tenantId;
    private Integer contentVersion;
    private String contentHash;
    private Long policyVersionId;
    private Long providerId;
    private String providerCode;
    private String modelIdentifier;
    private String taskStatus;
    private String decisionCode;
    private Integer confidenceBps;
    private String riskLabelsJson;
    private String reasonCode;
    private String providerRequestId;
    private String providerEventId;
    private Long durationMs;
    private Integer attemptCount;
    private LocalDateTime nextRetryTime;
    private String workerId;
    private LocalDateTime leaseUntil;
    private LocalDateTime claimedTime;
    private String lastErrorCode;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
