package com.plagod.dto;

import lombok.Data;

import java.util.List;

@Data
public class ClientSignalEvent {

    private String deviceCode;

    private List<ClientSignalItem> clients;

    @Data
    public static class ClientSignalItem {
        private String mac;
        private Long sessionId;
        private Integer rssi;
        private String state;
    }
}
