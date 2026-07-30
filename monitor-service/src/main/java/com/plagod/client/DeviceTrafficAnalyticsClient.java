package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.vo.device.TrafficAnalyticsSourceVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "device-service", contextId = "deviceTrafficAnalyticsClient")
public interface DeviceTrafficAnalyticsClient {

    @GetMapping("/internal/analytics/traffic")
    ApiResponse<TrafficAnalyticsSourceVO> queryTraffic(@RequestParam(value = "userId", required = false) Long userId,
                                                       @RequestParam(value = "mac", required = false) String mac,
                                                       @RequestParam(value = "sessionId", required = false) Long sessionId,
                                                       @RequestParam(value = "nodeId", required = false) Long nodeId,
                                                       @RequestParam(value = "deviceCode", required = false) String deviceCode,
                                                       @RequestParam("startTime") String startTime,
                                                       @RequestParam("endTime") String endTime,
                                                       @RequestParam("bucketMinutes") Integer bucketMinutes,
                                                       @RequestParam("topLimit") Integer topLimit,
                                                       @RequestHeader("X-Internal-Token") String internalToken);
}