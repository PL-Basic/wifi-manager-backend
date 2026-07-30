package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.SignalAnalyticsService;
import com.plagod.vo.monitor.SignalAnalysisVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/internal/admin/analytics/signals")
public class InternalAdminSignalAnalyticsController {

    @Autowired
    private SignalAnalyticsService signalAnalyticsService;

    @GetMapping
    public ApiResponse<SignalAnalysisVO> query(@RequestParam Long nodeId,
                                               @RequestParam String mac,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                               @RequestParam(defaultValue = "31") Integer sampleLimit,
                                               @RequestParam(defaultValue = "5") Integer bucketMinutes) {

        return ApiResponse.success(signalAnalyticsService.query(nodeId, mac, startTime, endTime, sampleLimit, bucketMinutes));
    }
}