package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.ClientLocationService;
import com.plagod.vo.monitor.ClientLocationPageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/internal/admin/locations")
public class InternalAdminLocationController {

    @Autowired
    private ClientLocationService clientLocationService;

    @GetMapping
    public ApiResponse<ClientLocationPageResult> pageLocations(@RequestParam(defaultValue = "1") Long current,
                                                               @RequestParam(defaultValue = "10") Long size,
                                                               @RequestParam(required = false) String mac,
                                                               @RequestParam(required = false) Long userId,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        return ApiResponse.success(clientLocationService.pageLocations(current, size, mac, userId, startTime, endTime));
    }
}