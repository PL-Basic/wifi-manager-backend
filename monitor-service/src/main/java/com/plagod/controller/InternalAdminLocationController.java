package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.security.MonitorTrustedRequestContextProvider;
import com.plagod.service.ClientLocationService;
import com.plagod.vo.monitor.ClientLocationPageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/internal/admin/locations")
public class InternalAdminLocationController {

    @Autowired
    private ClientLocationService clientLocationService;

    @Autowired
    private MonitorTrustedRequestContextProvider contextProvider;

    @GetMapping
    public ApiResponse<ClientLocationPageResult> pageLocations(@RequestParam(defaultValue = "1") Long current,
                                                               @RequestParam(defaultValue = "10") Long size,
                                                               @RequestParam(required = false) String mac,
                                                               @RequestParam(required = false) Long userId,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                                               HttpServletRequest request) {

        return ApiResponse.success(clientLocationService.pageLocations(
                contextProvider.resolve(request),
                current,
                size,
                mac,
                userId,
                startTime,
                endTime));
    }
}
