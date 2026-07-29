package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.SessionQueryService;
import com.plagod.vo.device.LocationSessionContextVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/location-sessions")
public class InternalLocationSessionController {

    @Autowired
    private SessionQueryService sessionQueryService;

    @GetMapping("/{sessionId}")
    public ApiResponse<LocationSessionContextVO> getLocationContext(@PathVariable Long sessionId,
                                                                    @RequestHeader("X-User-Id") Long userId) {

        return ApiResponse.success(sessionQueryService.getLocationContext(userId, sessionId));
    }
}