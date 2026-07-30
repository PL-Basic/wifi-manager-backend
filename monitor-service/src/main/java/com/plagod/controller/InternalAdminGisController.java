package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.GisAnalyticsService;
import com.plagod.service.GisNodeCoverageService;
import com.plagod.vo.monitor.GisHeatmapVO;
import com.plagod.vo.monitor.GisNodeCoverageVO;
import com.plagod.vo.monitor.GisStayPointResultVO;
import com.plagod.vo.monitor.GisTrajectoryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/internal/admin/gis")
public class InternalAdminGisController {

    @Autowired
    private GisAnalyticsService gisAnalyticsService;
    @Autowired
    private GisNodeCoverageService gisNodeCoverageService;

    @GetMapping("/trajectory")
    public ApiResponse<GisTrajectoryVO> trajectory(@RequestParam Long sessionId,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                                   @RequestParam(defaultValue = "100") Double maximumAccuracyMeters) {

        return ApiResponse.success(gisAnalyticsService.queryTrajectory(sessionId, startTime, endTime, maximumAccuracyMeters));
    }

    @GetMapping("/stay-points")
    public ApiResponse<GisStayPointResultVO> stayPoints(@RequestParam Long sessionId,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                                        @RequestParam(defaultValue = "100") Double maximumAccuracyMeters,
                                                        @RequestParam(defaultValue = "50") Integer radiusMeters,
                                                        @RequestParam(defaultValue = "300") Long minimumStaySeconds) {

        return ApiResponse.success(gisAnalyticsService.queryStayPoints(sessionId, startTime, endTime, maximumAccuracyMeters, radiusMeters, minimumStaySeconds));
    }

    @GetMapping("/heatmap")
    public ApiResponse<GisHeatmapVO> heatmap(@RequestParam(required = false) Long userId,
                                             @RequestParam(required = false) Long sessionId,
                                             @RequestParam(required = false) Long nodeId,
                                             @RequestParam(required = false) String mac,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                             @RequestParam(defaultValue = "100") Double maximumAccuracyMeters,
                                             @RequestParam(defaultValue = "50") Integer gridSizeMeters) {

        return ApiResponse.success(gisAnalyticsService.queryHeatmap(userId, sessionId, nodeId, mac, startTime, endTime, maximumAccuracyMeters, gridSizeMeters));
    }
    @GetMapping("/node-coverage")
    public ApiResponse<GisNodeCoverageVO> nodeCoverage(@RequestParam Long sessionId,
                                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                                       @RequestParam(defaultValue = "100") Double maximumAccuracyMeters,
                                                       @RequestParam(defaultValue = "30") Integer matchToleranceSeconds) {

        return ApiResponse.success(gisNodeCoverageService.query(sessionId, startTime, endTime, maximumAccuracyMeters, matchToleranceSeconds));
    }

}