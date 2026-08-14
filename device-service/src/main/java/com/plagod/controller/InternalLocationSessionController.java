package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.SessionQueryService;
import com.plagod.vo.device.LocationSessionContextVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/location-sessions")
public class InternalLocationSessionController {

    @Autowired
    private SessionQueryService sessionQueryService;

    @GetMapping("/{sessionId}")
    public ApiResponse<LocationSessionContextVO> getLocationContext(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable Long sessionId,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(
                sessionQueryService.getLocationContext(tenantId, userId, sessionId));
    }
}
