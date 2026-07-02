package com.plagod.vo;

import com.plagod.vo.device.DevicePageResult;
import com.plagod.vo.device.DeviceStatsVO;
import com.plagod.vo.user.UserPageResult;
import com.plagod.vo.user.UserStatsVO;
import lombok.Data;

@Data
public class AdminDashboardVO {
    private UserStatsVO userStats;
    private DeviceStatsVO deviceStats;
    private UserPageResult recentUsers;
    private DevicePageResult recentDevices;
}
