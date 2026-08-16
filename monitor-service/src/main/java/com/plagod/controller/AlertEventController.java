package com.plagod.controller;

import com.plagod.vo.monitor.AlertEventPageResult;
import com.plagod.vo.monitor.AlertEventVO;
import com.plagod.dto.ApiResponse;
import com.plagod.security.MonitorTrustedRequestContextProvider;
import com.plagod.security.TrustedRequestContext;
import com.plagod.service.AlertEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/internal/admin/alerts")
public class AlertEventController {

    @Autowired
    private AlertEventService alertEventService;

    @Autowired
    private MonitorTrustedRequestContextProvider contextProvider;

    @GetMapping
    public ApiResponse<AlertEventPageResult> pageAlerts(@RequestParam(defaultValue = "1") Long current,
                                                        @RequestParam(defaultValue = "10") Long size,
                                                        @RequestParam(required = false) Integer level,
                                                        @RequestParam(required = false) Integer status,
                                                        @RequestParam(required = false) String mac,
                                                        @RequestParam(required = false)
                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                        @RequestParam(required = false)
                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                                        HttpServletRequest request) {
        return ApiResponse.success(alertEventService.pageAlerts(
                contextProvider.resolve(request),
                current,
                size,
                level,
                status,
                mac,
                startTime,
                endTime));
    }

    @GetMapping("/{id}")
    public ApiResponse<AlertEventVO> getAlert(@PathVariable Long id,
                                              HttpServletRequest request) {
        return ApiResponse.success(alertEventService.getAlert(
                contextProvider.resolve(request),
                id));
    }

    @PatchMapping("/{id}/handle")
    public ApiResponse<Void> handle(@PathVariable Long id,
                                    HttpServletRequest request) {
        TrustedRequestContext context = contextProvider.resolve(request);
        alertEventService.handle(context, id);

        return ApiResponse.success("告警已标记处理", null);
    }
}
