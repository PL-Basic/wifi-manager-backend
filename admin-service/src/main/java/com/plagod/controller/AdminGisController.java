package com.plagod.controller;

import com.plagod.client.MonitorServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.vo.monitor.GisHeatmapVO;
import com.plagod.vo.monitor.GisNodeCoverageVO;
import com.plagod.vo.monitor.GisStayPointResultVO;
import com.plagod.vo.monitor.GisTrajectoryVO;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.function.Supplier;

@RestController
@RequestMapping("/admin/gis")
public class AdminGisController {

    @Autowired
    private MonitorServiceClient monitorServiceClient;

    @GetMapping("/trajectory")
    public ApiResponse<GisTrajectoryVO> trajectory(@RequestParam Long sessionId,
                                                   @RequestParam String startTime,
                                                   @RequestParam String endTime,
                                                   @RequestParam(defaultValue = "100") Double maximumAccuracyMeters) {

        return callGis(() -> monitorServiceClient.queryTrajectory(sessionId, startTime, endTime, maximumAccuracyMeters));
    }

    @GetMapping("/stay-points")
    public ApiResponse<GisStayPointResultVO> stayPoints(@RequestParam Long sessionId,
                                                        @RequestParam String startTime,
                                                        @RequestParam String endTime,
                                                        @RequestParam(defaultValue = "100") Double maximumAccuracyMeters,
                                                        @RequestParam(defaultValue = "50") Integer radiusMeters,
                                                        @RequestParam(defaultValue = "300") Long minimumStaySeconds) {

        return callGis(() -> monitorServiceClient.queryStayPoints(sessionId, startTime, endTime, maximumAccuracyMeters, radiusMeters, minimumStaySeconds));
    }

    @GetMapping("/heatmap")
    public ApiResponse<GisHeatmapVO> heatmap(@RequestParam(required = false) Long userId,
                                             @RequestParam(required = false) Long sessionId,
                                             @RequestParam(required = false) Long nodeId,
                                             @RequestParam(required = false) String mac,
                                             @RequestParam String startTime,
                                             @RequestParam String endTime,
                                             @RequestParam(defaultValue = "100") Double maximumAccuracyMeters,
                                             @RequestParam(defaultValue = "50") Integer gridSizeMeters) {

        return callGis(() -> monitorServiceClient.queryHeatmap(userId, sessionId, nodeId, mac, startTime, endTime, maximumAccuracyMeters, gridSizeMeters));
    }

    @GetMapping("/node-coverage")
    public ApiResponse<GisNodeCoverageVO> nodeCoverage(@RequestParam Long sessionId,
                                                       @RequestParam String startTime,
                                                       @RequestParam String endTime,
                                                       @RequestParam(defaultValue = "100") Double maximumAccuracyMeters,
                                                       @RequestParam(defaultValue = "30") Integer matchToleranceSeconds) {

        return callGis(() -> monitorServiceClient.queryNodeCoverage(sessionId, startTime, endTime, maximumAccuracyMeters, matchToleranceSeconds));
    }

    private <T> ApiResponse<T> callGis(
            Supplier<ApiResponse<T>> request) {
        try {
            return request.get();
        } catch (FeignException exception) {
            if (exception.status() == 400) {
                throw new IllegalArgumentException("GIS查询参数无效");
            }
            throw exception;
        }
    }
}