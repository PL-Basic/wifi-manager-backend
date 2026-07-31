package com.plagod.controller;

import com.plagod.client.DeviceServiceClient;
import com.plagod.client.MonitorServiceClient;
import com.plagod.client.UserServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.vo.AdminDashboardVO;
import com.plagod.vo.AdminOverviewVO;
import com.plagod.vo.device.DevicePageResult;
import com.plagod.vo.device.DeviceStatsVO;
import com.plagod.vo.user.UserPageResult;
import com.plagod.vo.user.UserStatsVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

@RestController
@RequestMapping("/admin")
public class AdminOverviewController {

    private static final Logger log = LoggerFactory.getLogger(AdminOverviewController.class);

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private DeviceServiceClient deviceServiceClient;

    @Autowired
    private MonitorServiceClient monitorServiceClient;

    @GetMapping("/overview")
    public ApiResponse<AdminOverviewVO> overview(@RequestHeader(value = "X-Gateway-Token", required = false) String gatewayToken) {

        DependencyResult<UserStatsVO> userResult = callDependency("user-service", userServiceClient::getUserStats);

        DependencyResult<DeviceStatsVO> deviceResult = callDependency("device-service", deviceServiceClient::getDeviceStats);

        DependencyResult<String> monitorResult = callDependency("monitor-service", monitorServiceClient::health);

        AdminOverviewVO overview = new AdminOverviewVO();

        // 只有请求携带经过安全过滤器验证的 Gateway Token，
        // 才能说明本次调用确实经过 Gateway。
        overview.setGatewayStatus(StringUtils.hasText(gatewayToken) ? "UP" : "UNKNOWN");

        overview.setUserServiceStatus(userResult.getStatus());

        overview.setDeviceServiceStatus(deviceResult.getStatus());

        overview.setMonitorServiceStatus(monitorResult.getStatus());

        overview.setUserStats(userResult.getData());
        overview.setDeviceStats(deviceResult.getData());

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
        return callDependency("user-service", userServiceClient::getUserStats).getData();
    }

    private DeviceStatsVO safeDeviceStats() {
        return callDependency("device-service", deviceServiceClient::getDeviceStats).getData();
    }

    private UserPageResult safeRecentUsers() {
        return callDependency("user-service", () -> userServiceClient.pageUsers(1L, 5L, null)).getData();
    }

    private DevicePageResult safeRecentDevices() {
        return callDependency("device-service", () -> deviceServiceClient.pageDevices(1L, 5L, null)).getData();
    }

    private <T> DependencyResult<T> callDependency(String serviceName, Supplier<ApiResponse<T>> invocation) {

        try {
            ApiResponse<T> response = invocation.get();

            if (response == null) {
                log.warn("{} 健康调用未返回响应", serviceName);
                return DependencyResult.degraded();
            }

            if (response.getCode() != 200 || response.getData() == null) {

                log.warn("{} 健康调用返回异常业务结果，code={}", serviceName, response.getCode());

                return DependencyResult.degraded();
            }

            return DependencyResult.up(response.getData());

        } catch (Exception exception) {
            // 不输出下游响应体和请求 Header，避免日志带入敏感信息。
            log.warn("{} 健康调用失败，exception={}", serviceName, exception.getClass().getSimpleName());

            return DependencyResult.down();
        }
    }

    private static final class DependencyResult<T> {

        private final String status;
        private final T data;

        private DependencyResult(String status, T data) {
            this.status = status;
            this.data = data;
        }

        private static <T> DependencyResult<T> up(T data) {
            return new DependencyResult<>("UP", data);
        }

        private static <T> DependencyResult<T> degraded() {
            return new DependencyResult<>("DEGRADED", null);
        }

        private static <T> DependencyResult<T> down() {
            return new DependencyResult<>("DOWN", null);
        }

        private String getStatus() {
            return status;
        }

        private T getData() {
            return data;
        }
    }
}