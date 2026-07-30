package com.plagod.controller;


import com.plagod.client.MonitorServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.monitor.GeofenceCreateDTO;
import com.plagod.dto.monitor.GeofenceUpdateDTO;
import com.plagod.vo.monitor.GeofenceEventPageResult;
import com.plagod.vo.monitor.GeofencePageResult;
import com.plagod.vo.monitor.GeofenceVO;

import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.function.Supplier;

@RestController
@RequestMapping("/admin/geofences")
public class AdminGeofenceController {

    @Autowired
    private MonitorServiceClient monitorServiceClient;

    @PostMapping
    public ApiResponse<GeofenceVO> createGeofence(@Valid @RequestBody GeofenceCreateDTO dto){
        return callGeofence(() ->monitorServiceClient.createGeofence(dto));
    }

    @PutMapping("/{fenceId}")
    public ApiResponse<GeofenceVO> updateGeofence(@PathVariable("fenceId") Long fenceId,
                                           @Valid @RequestBody GeofenceUpdateDTO dto){
        return callGeofence(() ->monitorServiceClient.updateGeofence(fenceId, dto));
    }

    @PatchMapping("/{fenceId}/enabled")
    public ApiResponse<GeofenceVO> toggleGeofence(@PathVariable("fenceId") Long fenceId,
                                           @RequestParam("enabled") Integer enabled){
        return callGeofence(() ->monitorServiceClient.toggleGeofence(fenceId, enabled));
    }

    @DeleteMapping("/{fenceId}")
    public ApiResponse<Void> deleteGeofence(@PathVariable("fenceId") Long fenceId){
        return callGeofence(() ->monitorServiceClient.deleteGeofence(fenceId));
    }

    @GetMapping("/{fenceId}")
    public ApiResponse<GeofenceVO> getGeofence(@PathVariable("fenceId") Long fenceId){
        return callGeofence(() ->monitorServiceClient.getGeofence(fenceId));
    }

    @GetMapping
    public ApiResponse<GeofencePageResult> pageGeofences(@RequestParam(value = "current", defaultValue = "1") Long current,
                                                         @RequestParam(value = "size", defaultValue = "10") Long size,
                                                         @RequestParam(value = "enabled", required = false) Integer enabled,
                                                         @RequestParam(value = "keyword", required = false) String keyword){
        return callGeofence(() ->monitorServiceClient.pageGeofences(current, size, enabled, keyword));
    }

    @GetMapping("/events")
    public ApiResponse<GeofenceEventPageResult> pageGeofenceEvents(@RequestParam(value = "current",defaultValue = "1") Long current,
                                                                   @RequestParam(value = "size",defaultValue = "10") Long size,
                                                                   @RequestParam(value = "fenceId", required = false) Long fenceId,
                                                                   @RequestParam(value = "userId", required = false) Long userId,
                                                                   @RequestParam(value = "sessionId", required = false) Long sessionId,
                                                                   @RequestParam(value = "mac", required = false) String mac,
                                                                   @RequestParam(value = "eventType", required = false) String eventType,
                                                                   @RequestParam(value = "startTime", required = false) String startTime,
                                                                   @RequestParam(value = "endTime", required = false) String endTime){
        return callGeofence(() ->monitorServiceClient.pageGeofenceEvents(current, size, fenceId, userId, sessionId, mac, eventType, startTime, endTime));
    }

    private <T> ApiResponse<T> callGeofence(Supplier<ApiResponse<T>> request) {
        try {
            return request.get();
        } catch (FeignException exception) {
            if (exception.status() == 400) {
                throw new IllegalArgumentException("围栏请求参数或状态无效");
            }
            throw exception;
        }
    }

}