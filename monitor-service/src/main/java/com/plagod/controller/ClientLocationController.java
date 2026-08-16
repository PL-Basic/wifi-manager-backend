package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.ClientLocationReportDTO;
import com.plagod.security.MonitorTrustedRequestContextProvider;
import com.plagod.service.ClientLocationService;
import com.plagod.vo.monitor.ClientLocationPageResult;
import com.plagod.vo.monitor.LocationAuthorizationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/locations")
public class ClientLocationController {

    @Autowired
    private ClientLocationService clientLocationService;

    @Autowired
    private MonitorTrustedRequestContextProvider contextProvider;

    @PostMapping("/sessions/{sessionId}/report")
    public ApiResponse<Long> report(@PathVariable Long sessionId,
                                    @Valid @RequestBody ClientLocationReportDTO dto,
                                    HttpServletRequest request) {

        return ApiResponse.success(
                "位置上报成功",
                clientLocationService.report(
                        contextProvider.resolve(request),
                        sessionId,
                        dto));
    }

    @GetMapping("/consent")
    public ApiResponse<LocationAuthorizationVO> getConsent(
            HttpServletRequest request) {

        return ApiResponse.success(clientLocationService.getAuthorization(
                contextProvider.resolve(request)));
    }

    @PostMapping("/consent")
    public ApiResponse<LocationAuthorizationVO> grantConsent(
            HttpServletRequest request) {

        return ApiResponse.success(
                "位置共享已开启",
                clientLocationService.grantAuthorization(
                        contextProvider.resolve(request)));
    }

    @DeleteMapping("/consent")
    public ApiResponse<LocationAuthorizationVO> revokeConsent(
            HttpServletRequest request) {

        return ApiResponse.success(
                "位置共享已撤销",
                clientLocationService.revokeAuthorization(
                        contextProvider.resolve(request)));
    }

    @DeleteMapping("/history")
    public ApiResponse<Long> clearHistory(HttpServletRequest request) {
        return ApiResponse.success(
                "本人位置历史已清除",
                clientLocationService.clearOwnedHistory(
                        contextProvider.resolve(request)));
    }

    @GetMapping
    public ApiResponse<ClientLocationPageResult> pageOwnedLocations(@RequestParam(defaultValue = "1") Long current,
                                                                    @RequestParam(defaultValue = "10") Long size,
                                                                    @RequestParam(required = false) String mac,
                                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                                                    HttpServletRequest request) {

        return ApiResponse.success(clientLocationService.pageOwnedLocations(
                contextProvider.resolve(request),
                current,
                size,
                mac,
                startTime,
                endTime));
    }
}
