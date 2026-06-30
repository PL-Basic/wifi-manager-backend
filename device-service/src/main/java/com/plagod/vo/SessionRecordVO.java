package com.plagod.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SessionRecordVO {
    private Long sessionId;
    private Long userId;
    private Long nodeId;
    private String mac;
    private String ip;
    private String deviceInfo;
    private LocalDateTime loginTime;
    private LocalDateTime expireTime;
    private LocalDateTime logoutTime;
    private Integer status;
    private Long bytesUp;
    private Long bytesDown;
}
