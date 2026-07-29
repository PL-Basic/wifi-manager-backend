package com.plagod.vo.device;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LocationSessionContextVO {

    private Long sessionId;
    private Long userId;
    private Long nodeId;
    private String deviceCode;
    private String mac;
    private LocalDateTime expireTime;
    private LocalDateTime lastSeenTime;
    private LocalDateTime nodeLastHeartbeat;
}