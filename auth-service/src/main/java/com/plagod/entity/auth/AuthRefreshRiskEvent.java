package com.plagod.entity.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_auth_refresh_risk_event")
public class AuthRefreshRiskEvent {

    @TableId(type = IdType.AUTO)
    private Long eventId;
    private String sessionId;
    private String eventType;
    private String previousSignalHash;
    private String currentSignalHash;
    private LocalDateTime createTime;
}
