package com.plagod.vo;

import com.plagod.vo.device.DevicePageResult;
import com.plagod.vo.device.DeviceStatsVO;
import com.plagod.vo.user.UserPageResult;
import com.plagod.vo.user.UserStatsVO;
import com.plagod.vo.device.TrafficAnalyticsSourceVO;
import lombok.Data;

@Data
public class AdminDashboardVO {
    private UserStatsVO userStats;
    private DeviceStatsVO deviceStats;
    private UserPageResult recentUsers;
    private DevicePageResult recentDevices;
    private TrafficAnalyticsSourceVO.Summary trafficSummary;
    private Long unhandledAlertCount;
    private Long enabledRuleCount;
    private Long locationCount;
}
