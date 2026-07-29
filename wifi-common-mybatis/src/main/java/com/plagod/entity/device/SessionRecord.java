package com.plagod.entity.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_session")
public class SessionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "session_id", type = IdType.AUTO)
    private Long sessionId;

    private Long userId;

    private Long entitlementId;

    // 当前等待 Session 所替换的旧 Session。
    private Long replacedSessionId;

    private String authorizationMode;

    private Long nodeId;

    private String mac;

    private String ip;

    private String deviceInfo;

    private LocalDateTime loginTime;

    private LocalDateTime expireTime;

    private LocalDateTime lastSeenTime;

    private LocalDateTime lastRenewTime;

    private LocalDateTime lastBilledTime;

    private Long consumedSeconds;

    private String endReason;

    private LocalDateTime logoutTime;

    private Integer status;

    private Long bytesUp;

    private Long bytesDown;
}
