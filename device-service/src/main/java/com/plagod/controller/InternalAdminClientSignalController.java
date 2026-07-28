package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.ClientSignalQueryService;
import com.plagod.vo.device.ClientSignalPageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/internal/admin/client-signals")
public class InternalAdminClientSignalController {

    @Autowired
    private ClientSignalQueryService clientSignalQueryService;

    @GetMapping
    public ApiResponse<ClientSignalPageResult> pageClientSignals(@RequestParam(defaultValue = "1") Long current,
                                                                 @RequestParam(defaultValue = "10") Long size,
                                                                 @RequestParam(required = false) String deviceCode,
                                                                 @RequestParam(required = false) Long nodeId,
                                                                 @RequestParam(required = false) String mac,
                                                                 @RequestParam(required = false) Long sessionId,
                                                                 @RequestParam(required = false) String state, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        return ApiResponse.success(clientSignalQueryService.pageClientSignals(current, size, deviceCode, nodeId, mac, sessionId, state, startTime, endTime));
    }
}