package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.DeviceSignalAnalyticsQueryService;
import com.plagod.service.DeviceTrafficAnalyticsQueryService;
import com.plagod.vo.device.SignalAnalyticsSourceVO;
import com.plagod.vo.device.TrafficAnalyticsSourceVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/internal/analytics")
public class InternalDeviceAnalyticsController {

    @Autowired
    private DeviceSignalAnalyticsQueryService signalAnalyticsQueryService;

    @Autowired
    private DeviceTrafficAnalyticsQueryService trafficAnalyticsQueryService;

    /**
     * 向 monitor-service 提供 RSSI 原始样本、趋势和节点标定参数。
     */
    @GetMapping("/signals")
    public ApiResponse<SignalAnalyticsSourceVO> querySignals(@RequestParam Long nodeId,
                                                             @RequestParam String mac,
                                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                                             @RequestParam(defaultValue = "31") Integer sampleLimit,
                                                             @RequestParam(defaultValue = "5") Integer bucketMinutes) {

        return ApiResponse.success(signalAnalyticsQueryService.query(nodeId, mac, startTime, endTime, sampleLimit, bucketMinutes));
    }

    @GetMapping("/traffic")
    public ApiResponse<TrafficAnalyticsSourceVO> queryTraffic(@RequestParam(required = false) Long userId,
                                                              @RequestParam(required = false) String mac,
                                                              @RequestParam(required = false) Long sessionId,
                                                              @RequestParam(required = false) Long nodeId,
                                                              @RequestParam(required = false) String deviceCode,
                                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                                              @RequestParam(defaultValue = "60") Integer bucketMinutes,
                                                              @RequestParam(defaultValue = "10") Integer topLimit) {

        return ApiResponse.success(trafficAnalyticsQueryService.query(userId, mac, sessionId, nodeId, deviceCode, startTime, endTime, bucketMinutes, topLimit));
    }
}