package com.plagod.dto;

import lombok.Data;

@Data
public class DeviceStatsVO {
    private Long totalNodes;
    private Long onlineNodes;
    private Long offlineNodes;
    private Long currentClients;
    private Long onlineSessions;
    private Long blacklistCount;
}
