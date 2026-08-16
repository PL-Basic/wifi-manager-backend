package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_announcement_action_request")
public class AnnouncementActionRequest {

    @TableId(type = IdType.AUTO)
    private Long actionRequestId;
    private String scopeType;
    private Long tenantId;
    @TableField(
            insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private String scopeKey;
    private Long announcementId;
    private Long actorUserId;
    private String actionType;
    private String clientRequestId;
    private String requestFingerprint;
    private Integer expectedVersion;
    private String requestStatus;
    private String resultAnnouncementStatus;
    private Integer resultVersion;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
