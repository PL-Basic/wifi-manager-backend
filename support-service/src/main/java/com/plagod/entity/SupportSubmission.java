package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_support_submission")
public class SupportSubmission {

    @TableId(type = IdType.AUTO)
    private Long submissionId;
    private Long tenantId;
    private Long userId;
    private String clientRequestId;
    private String requestFingerprint;
    private String title;
    private String contentText;
    private String contentHash;
    private Integer contentVersion;
    private LocalDateTime acceptedTime;
    private LocalDate quotaDate;
    private String status;
    private String reviewRequestId;
    private Long aiReviewTaskId;
    private String reviewDecision;
    private String outcomeReasonCode;
    private Long ticketId;
    private Integer limitRefunded;
    private String quotaRefundEventKey;
    private LocalDateTime quotaRefundTime;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
