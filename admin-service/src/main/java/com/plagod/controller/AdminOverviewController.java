package com.plagod.controller;

import com.plagod.client.DeviceServiceClient;
import com.plagod.client.UserServiceClient;
import com.plagod.vo.AdminDashboardVO;
import com.plagod.vo.AdminOverviewVO;
import com.plagod.dto.ApiResponse;
import com.plagod.vo.device.DeviceStatsVO;
import com.plagod.vo.user.UserPageResult;
import com.plagod.vo.user.UserStatsVO;
import com.plagod.vo.device.DevicePageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminOverviewController {

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private DeviceServiceClient deviceServiceClient;

    @GetMapping("/overview")
    public ApiResponse<AdminOverviewVO> overview() {
        UserStatsVO userStats = safeUserStats();
        DeviceStatsVO deviceStats = safeDeviceStats();

        AdminOverviewVO overview = new AdminOverviewVO();
        overview.setGatewayStatus("UP");
        overview.setUserServiceStatus("UP");
        overview.setDeviceServiceStatus("UP");
        overview.setMonitorServiceStatus("NOT_CREATED");
        overview.setUserStats(userStats);
        overview.setDeviceStats(deviceStats);
        return ApiResponse.success(overview);
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardVO> dashboard() {
        AdminDashboardVO dashboard = new AdminDashboardVO();
        dashboard.setUserStats(safeUserStats());
        dashboard.setDeviceStats(safeDeviceStats());
        dashboard.setRecentUsers(safeRecentUsers());
        dashboard.setRecentDevices(safeRecentDevices());
        return ApiResponse.success(dashboard);
    }

    private UserStatsVO safeUserStats() {
        try {
            ApiResponse<UserStatsVO> response = userServiceClient.getUserStats();
            return response == null ? null : response.getData();
        } catch (Exception ex) {
            return null;
        }
    }

    private DeviceStatsVO safeDeviceStats() {
        try {
            ApiResponse<DeviceStatsVO> response = deviceServiceClient.getDeviceStats();
            return response == null ? null : response.getData();
        } catch (Exception ex) {
            return null;
        }
    }

    private UserPageResult safeRecentUsers() {
        try {
            ApiResponse<UserPageResult> response = userServiceClient.pageUsers(1L, 5L, null);
            return response == null ? null : response.getData();
        } catch (Exception ex) {
            return null;
        }
    }

    private DevicePageResult safeRecentDevices() {
        try {
            ApiResponse<DevicePageResult> response = deviceServiceClient.pageDevices(1L, 5L, null);
            return response == null ? null : response.getData();
        } catch (Exception ex) {
            return null;
        }
    }
}
