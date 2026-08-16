package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.TrafficQueryService;
import com.plagod.vo.device.TrafficPageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/internal/admin/traffic")
public class InternalAdminTrafficController {

    @Autowired
    private TrafficQueryService trafficQueryService;

    @GetMapping
    public ApiResponse<TrafficPageResult> pageTraffic(@RequestHeader("X-Tenant-Id") Long tenantId,
                                                      @RequestParam(defaultValue = "1") Long current,
                                                      @RequestParam(defaultValue = "10") Long size,
                                                      @RequestParam(required = false) String mac,
                                                      @RequestParam(required = false) Long sessionId,
                                                      @RequestParam(required = false) String dstIp,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        return ApiResponse.success(trafficQueryService.pageTraffic(tenantId, current, size, mac, sessionId, dstIp, startTime, endTime));
    }
}
