package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.TrafficAnalyticsService;
import com.plagod.vo.device.TrafficAnalyticsSourceVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/internal/admin/analytics/traffic")
public class InternalAdminTrafficAnalyticsController {

    @Autowired
    private TrafficAnalyticsService trafficAnalyticsService;

    @GetMapping
    public ApiResponse<TrafficAnalyticsSourceVO> query(@RequestParam(required = false) Long userId,
                                                       @RequestParam(required = false) String mac,
                                                       @RequestParam(required = false) Long sessionId,
                                                       @RequestParam(required = false) Long nodeId,
                                                       @RequestParam(required = false) String deviceCode,
                                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                                       @RequestParam(defaultValue = "60") Integer bucketMinutes,
                                                       @RequestParam(defaultValue = "10") Integer topLimit) {

        return ApiResponse.success(trafficAnalyticsService.query(userId, mac, sessionId, nodeId, deviceCode, startTime, endTime, bucketMinutes, topLimit));
    }
}