package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_announcement_content_version")
public class AnnouncementContentVersion {

    @TableId(type = IdType.AUTO)
    private Long announcementContentId;
    private Long announcementId;
    private Integer contentVersion;
    private String title;
    private String summary;
    private String bodyText;
    private String contentHash;
    private String reviewRequestId;
    private Long aiReviewTaskId;
    private String reviewDecision;
    private String reviewReasonCode;
    private Integer reviewConfidenceBps;
    private String status;
    private LocalDateTime publishedTime;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
