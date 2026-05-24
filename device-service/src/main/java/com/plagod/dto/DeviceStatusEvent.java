package com.plagod.dto;

import lombok.Data;

@Data
public class DeviceStatusEvent {
    private String deviceCode;
    private String ip;
    private String firmwareVersion;
    private Integer status;
    private Integer currentClients;
}
