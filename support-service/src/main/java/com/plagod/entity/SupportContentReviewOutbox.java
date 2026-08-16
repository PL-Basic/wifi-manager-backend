package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_support_content_review_outbox")
public class SupportContentReviewOutbox {

    @TableId(type = IdType.AUTO)
    private Long eventId;
    private String reviewRequestId;
    private String scene;
    private String businessType;
    private Long businessId;
    private Long tenantId;
    private Integer contentVersion;
    private String contentHash;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private LocalDateTime leaseUntil;
    private String workerId;
    private String lastErrorCode;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
