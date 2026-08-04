package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class GatewayHealthController {

    @GetMapping("/health/gateway")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "gateway-service");
        result.put("status", "UP");
        result.put("timestamp", System.currentTimeMillis());
        return ApiResponse.success(result);
    }
}
