package com.plagod.dto;

import lombok.Data;

@Data
public class DeviceStatusEvent {
    private String deviceCode;
    private String ip;
    private String firmwareVersion;
    private String wifiStatus;
    private Integer status;
    private Integer currentClients;
    private String activeWifiConfigRequestId;
    private Long activeWifiConfigVersion;
    private String pendingWifiConfigRequestId;
    private Long pendingWifiConfigVersion;
}
