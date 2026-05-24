package com.plagod.dto;

import lombok.Data;

@Data
public class DeviceTrafficEvent {
    private String deviceCode;
    private Long sessionId;
    private String mac;
    private String dstIp;
    private Integer dstPort;
    private String sni;
    private String protocol;
    private Long bytesUp;
    private Long bytesDown;
}
