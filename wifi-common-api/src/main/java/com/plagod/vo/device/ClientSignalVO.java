package com.plagod.vo.device;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ClientSignalVO {

    private Long id;
    private Long nodeId;
    private String deviceCode;
    private String mac;
    private Long sessionId;
    private Integer rssi;
    private String state;
    private LocalDateTime reportTime;
}
