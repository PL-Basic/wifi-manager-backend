package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_announcement")
public class Announcement {

    @TableId(type = IdType.AUTO)
    private Long announcementId;
    private String scopeType;
    private Long tenantId;
    @TableField(
            insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private String scopeKey;
    private Long authorUserId;
    private String authorDisplayName;
    private String clientRequestId;
    private String requestFingerprint;
    private Integer currentContentVersion;
    private Integer publishedContentVersion;
    private String status;
    private Integer pinned;
    private Integer allowComments;
    private Integer version;
    private Integer delFlag;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
