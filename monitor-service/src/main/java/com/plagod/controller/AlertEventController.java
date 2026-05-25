package com.plagod.controller;

import com.plagod.dto.AlertEventPageResult;
import com.plagod.dto.AlertEventVO;
import com.plagod.dto.ApiResponse;
import com.plagod.service.AlertEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/alerts")
public class AlertEventController {

    @Autowired
    private AlertEventService alertEventService;

    @GetMapping
    public ApiResponse<AlertEventPageResult> pageAlerts(@RequestParam(defaultValue = "1") Long current,
                                                        @RequestParam(defaultValue = "10") Long size,
                                                        @RequestParam(required = false) Integer level,
                                                        @RequestParam(required = false) Integer status,
                                                        @RequestParam(required = false) String mac,
                                                        @RequestParam(required = false)
                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                        @RequestParam(required = false)
                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResponse.success(alertEventService.pageAlerts(current, size, level, status, mac, startTime, endTime));
    }

    @GetMapping("/{id}")
    public ApiResponse<AlertEventVO> getAlert(@PathVariable Long id) {
        return ApiResponse.success(alertEventService.getAlert(id));
    }

    @PatchMapping("/{id}/handle")
    public ApiResponse<Void> handle(@PathVariable Long id, @RequestParam Long handleUserId) {
        alertEventService.handle(id, handleUserId);
        return ApiResponse.success("告警已标记处理", null);
    }
}
