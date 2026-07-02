package com.plagod.vo;

import com.plagod.vo.device.DeviceStatsVO;
import com.plagod.vo.user.UserStatsVO;
import lombok.Data;

@Data
public class AdminOverviewVO {
    private String gatewayStatus;
    private String userServiceStatus;
    private String deviceServiceStatus;
    private String monitorServiceStatus;
    private UserStatsVO userStats;
    private DeviceStatsVO deviceStats;
}
