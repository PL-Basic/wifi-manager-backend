package com.plagod.dto;

import lombok.Data;

@Data
public class AdminDashboardVO {
    private UserStatsVO userStats;
    private DeviceStatsVO deviceStats;
    private Object recentUsers;
    private Object recentDevices;
}
