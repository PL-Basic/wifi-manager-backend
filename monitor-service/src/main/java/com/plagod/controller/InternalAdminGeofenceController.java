package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.monitor.GeofenceCreateDTO;
import com.plagod.dto.monitor.GeofenceUpdateDTO;
import com.plagod.service.GeofenceAdminService;
import com.plagod.vo.monitor.GeofenceEventPageResult;
import com.plagod.vo.monitor.GeofencePageResult;
import com.plagod.vo.monitor.GeofenceVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/internal/admin")
public class InternalAdminGeofenceController {

    @Autowired
    private GeofenceAdminService service;

    @PostMapping("/geofences")
    public ApiResponse<GeofenceVO> create(@Valid @RequestBody GeofenceCreateDTO dto) {
        return ApiResponse.success(service.create(dto));
    }

    @PutMapping("/geofences/{fenceId}")
    public ApiResponse<GeofenceVO> update(@PathVariable Long fenceId,
                                          @Valid @RequestBody GeofenceUpdateDTO dto) {
        return ApiResponse.success(service.update(fenceId, dto));
    }

    @PatchMapping("/geofences/{fenceId}/enabled")
    public ApiResponse<GeofenceVO> toggle(@PathVariable Long fenceId,
                                          @RequestParam Integer enabled) {
        return ApiResponse.success(service.toggle(fenceId, enabled));
    }

    @DeleteMapping("/geofences/{fenceId}")
    public ApiResponse<Void> delete(@PathVariable Long fenceId) {
        service.delete(fenceId);
        return ApiResponse.success(null);
    }

    @GetMapping("/geofences/{fenceId}")
    public ApiResponse<GeofenceVO> get(@PathVariable Long fenceId) {
        return ApiResponse.success(service.get(fenceId));
    }

    @GetMapping("/geofences")
    public ApiResponse<GeofencePageResult> page(@RequestParam(defaultValue = "1") Long current,
                                                @RequestParam(defaultValue = "10") Long size,
                                                @RequestParam(required = false) Integer enabled,
                                                @RequestParam(required = false) String keyword) {
        return ApiResponse.success(service.page(current, size, enabled, keyword));
    }

    @GetMapping("/geofence-events")
    public ApiResponse<GeofenceEventPageResult> events(@RequestParam(defaultValue = "1") Long current,
                                                       @RequestParam(defaultValue = "10") Long size,
                                                       @RequestParam(required = false) Long fenceId,
                                                       @RequestParam(required = false) Long userId,
                                                       @RequestParam(required = false) Long sessionId,
                                                       @RequestParam(required = false) String mac,
                                                       @RequestParam(required = false) String eventType,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        return ApiResponse.success(service.pageEvents(current, size, fenceId, userId, sessionId, mac, eventType, startTime, endTime));
    }
}