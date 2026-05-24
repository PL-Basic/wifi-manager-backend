package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.TrafficPageResult;
import com.plagod.service.TrafficQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/traffic")
public class TrafficController {

    @Autowired
    private TrafficQueryService trafficQueryService;

    @GetMapping
    public ApiResponse<TrafficPageResult> pageTraffic(@RequestParam(defaultValue = "1") Long current,
                                                      @RequestParam(defaultValue = "10") Long size,
                                                      @RequestParam(required = false) String mac,
                                                      @RequestParam(required = false) Long sessionId,
                                                      @RequestParam(required = false) String dstIp,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResponse.success(trafficQueryService.pageTraffic(current, size, mac, sessionId, dstIp, startTime, endTime));
    }
}
