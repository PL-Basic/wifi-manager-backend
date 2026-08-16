package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_announcement_comment")
public class AnnouncementComment {

    @TableId(type = IdType.AUTO)
    private Long commentId;
    private Long tenantId;
    private Long announcementId;
    private Long parentCommentId;
    private Long authorUserId;
    private String authorDisplayName;
    private String clientRequestId;
    private String requestFingerprint;
    private String contentText;
    private String status;
    private Long hiddenByUserId;
    private String hiddenReasonCode;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
