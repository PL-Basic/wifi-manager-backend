package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.monitor.AccessRuleCreateDTO;
import com.plagod.dto.monitor.AccessRuleUpdateDTO;
import com.plagod.dto.monitor.GeofenceCreateDTO;
import com.plagod.dto.monitor.GeofenceUpdateDTO;
import com.plagod.vo.device.TrafficAnalyticsSourceVO;
import com.plagod.vo.monitor.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@FeignClient(name = "monitor-service")
public interface MonitorServiceClient {

    @GetMapping("/internal/admin/health")
    ApiResponse<String> health();

    @GetMapping("/internal/admin/rules")
    ApiResponse<AccessRulePageResult> pageRules(@RequestParam("current") Long current,
                                                @RequestParam("size") Long size,
                                                @RequestParam(value = "ruleType", required = false) Integer ruleType,
                                                @RequestParam(value = "enabled", required = false) Integer enabled,
                                                @RequestParam(value = "keyword", required = false) String keyword);

    @GetMapping("/internal/admin/rules/{id}")
    ApiResponse<AccessRuleVO> getRule(@PathVariable("id") Long id);

    @PostMapping("/internal/admin/rules")
    ApiResponse<AccessRuleVO> createRule(@Valid @RequestBody AccessRuleCreateDTO createDTO);

    @PutMapping("/internal/admin/rules/{id}")
    ApiResponse<AccessRuleVO> updateRule(@PathVariable("id") Long id,@Valid @RequestBody AccessRuleUpdateDTO updateDTO);

    @DeleteMapping("/internal/admin/rules/{id}")
    ApiResponse<Void> deleteRule(@PathVariable("id") Long id);

    @PatchMapping("/internal/admin/rules/{id}/enabled")
    ApiResponse<Void> toggleRule(@PathVariable("id") Long id, @RequestParam("enabled") Integer enabled);

    @GetMapping("/internal/admin/alerts")
    ApiResponse<AlertEventPageResult> pageAlerts(@RequestParam("current") Long current,
                                                 @RequestParam("size") Long size,
                                                 @RequestParam(value = "level", required = false) Integer level,
                                                 @RequestParam(value = "status", required = false) Integer status,
                                                 @RequestParam(value = "mac", required = false) String mac,
                                                 @RequestParam(value = "startTime", required = false) String startTime,
                                                 @RequestParam(value = "endTime", required = false) String endTime);

    @GetMapping("/internal/admin/alerts/{id}")
    ApiResponse<AlertEventVO> getAlert(@PathVariable("id") Long id);

    @PatchMapping("/internal/admin/alerts/{id}/handle")
    ApiResponse<Void> handleAlert(@PathVariable("id") Long id, @RequestHeader("X-User-Id") Long handleUserId);

    @GetMapping("/internal/admin/audits")
    ApiResponse<AuditLogPageResult> pageAudits(@RequestParam("current") Long current,
                                               @RequestParam("size") Long size,
                                               @RequestParam(value = "action", required = false) String action,
                                               @RequestParam(value = "operatorName", required = false) String operatorName,
                                               @RequestParam(value = "target", required = false) String target,
                                               @RequestParam(value = "startTime", required = false) String startTime,
                                               @RequestParam(value = "endTime", required = false) String endTime);

    @GetMapping("/internal/admin/audits/{id}")
    ApiResponse<AuditLogVO> getAudit(@PathVariable("id") Long id);

    @GetMapping("/internal/admin/locations")
    ApiResponse<ClientLocationPageResult> pageLocations(@RequestParam("current") Long current,
                                                        @RequestParam("size") Long size,
                                                        @RequestParam(value = "mac", required = false) String mac,
                                                        @RequestParam(value = "userId", required = false) Long userId,
                                                        @RequestParam(value = "startTime", required = false) String startTime,
                                                        @RequestParam(value = "endTime", required = false) String endTime);

    @GetMapping("/internal/admin/analytics/signals")
    ApiResponse<SignalAnalysisVO> querySignalAnalytics(@RequestParam("nodeId") Long nodeId,
                                                       @RequestParam("mac") String mac,
                                                       @RequestParam("startTime") String startTime,
                                                       @RequestParam("endTime") String endTime,
                                                       @RequestParam("sampleLimit") Integer sampleLimit,
                                                       @RequestParam("bucketMinutes") Integer bucketMinutes);

    @GetMapping("/internal/admin/analytics/traffic")
    ApiResponse<TrafficAnalyticsSourceVO> queryTrafficAnalytics(@RequestParam(value = "userId", required = false) Long userId,
                                                                @RequestParam(value = "mac", required = false) String mac,
                                                                @RequestParam(value = "sessionId", required = false) Long sessionId,
                                                                @RequestParam(value = "nodeId", required = false) Long nodeId,
                                                                @RequestParam(value = "deviceCode", required = false) String deviceCode,
                                                                @RequestParam("startTime") String startTime,
                                                                @RequestParam("endTime") String endTime,
                                                                @RequestParam("bucketMinutes") Integer bucketMinutes,
                                                                @RequestParam("topLimit") Integer topLimit);


    @GetMapping("/internal/admin/analytics/alerts-rules")
    ApiResponse<AlertRuleAnalyticsVO> queryAlertRuleAnalytics(@RequestParam(value = "userId", required = false) Long userId,
                                                              @RequestParam(value = "mac", required = false) String mac,
                                                              @RequestParam(value = "sessionId", required = false) Long sessionId,
                                                              @RequestParam(value = "nodeId", required = false) Long nodeId,
                                                              @RequestParam(value = "deviceCode", required = false) String deviceCode,
                                                              @RequestParam("startTime") String startTime,
                                                              @RequestParam("endTime") String endTime,
                                                              @RequestParam("topLimit") Integer topLimit);

    @GetMapping("/internal/admin/gis/trajectory")
    ApiResponse<GisTrajectoryVO> queryTrajectory(@RequestParam("sessionId") Long sessionId,
                                                 @RequestParam("startTime") String startTime,
                                                 @RequestParam("endTime") String endTime,
                                                 @RequestParam("maximumAccuracyMeters") Double maximumAccuracyMeters);

    @GetMapping("/internal/admin/gis/stay-points")
    ApiResponse<GisStayPointResultVO> queryStayPoints(@RequestParam("sessionId") Long sessionId,
                                                      @RequestParam("startTime") String startTime,
                                                      @RequestParam("endTime") String endTime,
                                                      @RequestParam("maximumAccuracyMeters") Double maximumAccuracyMeters,
                                                      @RequestParam("radiusMeters") Integer radiusMeters,
                                                      @RequestParam("minimumStaySeconds") Long minimumStaySeconds);

    @GetMapping("/internal/admin/gis/heatmap")
    ApiResponse<GisHeatmapVO> queryHeatmap(@RequestParam(value = "userId", required = false) Long userId,
                                           @RequestParam(value = "sessionId", required = false) Long sessionId,
                                           @RequestParam(value = "nodeId", required = false) Long nodeId,
                                           @RequestParam(value = "mac", required = false) String mac,
                                           @RequestParam("startTime") String startTime,
                                           @RequestParam("endTime") String endTime,
                                           @RequestParam("maximumAccuracyMeters") Double maximumAccuracyMeters,
                                           @RequestParam("gridSizeMeters") Integer gridSizeMeters);

    @PostMapping("/internal/admin/geofences")
    ApiResponse<GeofenceVO> createGeofence(@Valid @RequestBody GeofenceCreateDTO dto);

    @PutMapping("/internal/admin/geofences/{fenceId}")
    ApiResponse<GeofenceVO> updateGeofence(@PathVariable("fenceId") Long fenceId,
                                           @Valid @RequestBody GeofenceUpdateDTO dto);

    @PatchMapping("/internal/admin/geofences/{fenceId}/enabled")
    ApiResponse<GeofenceVO> toggleGeofence(@PathVariable("fenceId") Long fenceId,
                                           @RequestParam("enabled") Integer enabled);

    @DeleteMapping("/internal/admin/geofences/{fenceId}")
    ApiResponse<Void> deleteGeofence(@PathVariable("fenceId") Long fenceId);

    @GetMapping("/internal/admin/geofences/{fenceId}")
    ApiResponse<GeofenceVO> getGeofence(@PathVariable("fenceId") Long fenceId);

    @GetMapping("/internal/admin/geofences")
    ApiResponse<GeofencePageResult> pageGeofences(@RequestParam("current") Long current,
                                                  @RequestParam("size") Long size,
                                                  @RequestParam(value = "enabled", required = false) Integer enabled,
                                                  @RequestParam(value = "keyword", required = false) String keyword);

    @GetMapping("/internal/admin/geofence-events")
    ApiResponse<GeofenceEventPageResult> pageGeofenceEvents(@RequestParam("current") Long current,
                                                            @RequestParam("size") Long size,
                                                            @RequestParam(value = "fenceId", required = false) Long fenceId,
                                                            @RequestParam(value = "userId", required = false) Long userId,
                                                            @RequestParam(value = "sessionId", required = false) Long sessionId,
                                                            @RequestParam(value = "mac", required = false) String mac,
                                                            @RequestParam(value = "eventType", required = false) String eventType,
                                                            @RequestParam(value = "startTime", required = false) String startTime,
                                                            @RequestParam(value = "endTime", required = false) String endTime);

    @GetMapping("/internal/admin/gis/node-coverage")
    ApiResponse<GisNodeCoverageVO> queryNodeCoverage(@RequestParam("sessionId") Long sessionId,
                                                     @RequestParam("startTime") String startTime,
                                                     @RequestParam("endTime") String endTime,
                                                     @RequestParam("maximumAccuracyMeters") Double maximumAccuracyMeters,
                                                     @RequestParam("matchToleranceSeconds") Integer matchToleranceSeconds);
}