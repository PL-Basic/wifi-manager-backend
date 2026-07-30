package com.plagod.vo.device;

import lombok.Data;

import java.math.BigDecimal;
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
    private String wifiStatus;
    private Integer maxClients;
    private Integer currentClients;
    private LocalDateTime lastHeartbeat;

    private Integer rssiAtOneMeter;
    private BigDecimal pathLossExponent;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
