package com.plagod.entity.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_auth_session_revoke_outbox")
public class UserAuthSessionRevokeOutbox {

    @TableId(type = IdType.AUTO)
    private Long outboxId;
    private String eventId;
    private Long userId;
    private String revokeReason;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String workerId;
    private LocalDateTime leaseUntil;
    private LocalDateTime claimedTime;
    private LocalDateTime completedTime;
    private String lastErrorCode;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
