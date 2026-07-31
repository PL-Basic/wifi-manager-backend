package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.MonitorHealthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin")
public class InternalAdminHealthController {

    @Autowired
    private MonitorHealthService monitorHealthService;

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success(monitorHealthService.check());
    }
}