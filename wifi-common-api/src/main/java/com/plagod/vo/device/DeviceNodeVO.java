package com.plagod.vo.device;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceNodeVO {
    private Long nodeId;
    private String deviceCode;
    private String name;
    private String location;
    private String ip;
    private Integer status;
    private String firmwareVersion;
    private Integer maxClients;
    private Integer currentClients;
    private LocalDateTime lastHeartbeat;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
