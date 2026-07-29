package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.ClientLocationReportDTO;
import com.plagod.service.ClientLocationService;
import com.plagod.vo.monitor.ClientLocationPageResult;
import com.plagod.vo.monitor.LocationAuthorizationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/locations")
public class ClientLocationController {

    @Autowired
    private ClientLocationService clientLocationService;

    @PostMapping("/sessions/{sessionId}/report")
    public ApiResponse<Long> report(@PathVariable Long sessionId,
                                    @Valid @RequestBody ClientLocationReportDTO dto,
                                    @RequestHeader("X-User-Id") Long userId) {

        return ApiResponse.success("位置上报成功", clientLocationService.report(sessionId, dto, userId));
    }

    @GetMapping("/consent")
    public ApiResponse<LocationAuthorizationVO> getConsent(@RequestHeader("X-User-Id") Long userId) {

        return ApiResponse.success(clientLocationService.getAuthorization(userId));
    }

    @PostMapping("/consent")
    public ApiResponse<LocationAuthorizationVO> grantConsent(@RequestHeader("X-User-Id") Long userId) {

        return ApiResponse.success("位置共享已开启", clientLocationService.grantAuthorization(userId));
    }

    @DeleteMapping("/consent")
    public ApiResponse<LocationAuthorizationVO> revokeConsent(@RequestHeader("X-User-Id") Long userId) {

        return ApiResponse.success("位置共享已撤销", clientLocationService.revokeAuthorization(userId));
    }

    @DeleteMapping("/history")
    public ApiResponse<Long> clearHistory(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success("本人位置历史已清除", clientLocationService.clearOwnedHistory(userId));
    }

    @GetMapping
    public ApiResponse<ClientLocationPageResult> pageOwnedLocations(@RequestParam(defaultValue = "1") Long current,
                                                                    @RequestParam(defaultValue = "10") Long size,
                                                                    @RequestParam(required = false) String mac,
                                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                                                    @RequestHeader("X-User-Id") Long userId) {

        return ApiResponse.success(clientLocationService.pageOwnedLocations(userId, current, size, mac, startTime, endTime));
    }
}