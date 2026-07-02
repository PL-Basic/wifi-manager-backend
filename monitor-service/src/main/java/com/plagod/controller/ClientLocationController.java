package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.vo.monitor.ClientLocationPageResult;
import com.plagod.dto.ClientLocationReportDTO;
import com.plagod.service.ClientLocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/locations")
public class ClientLocationController {

    @Autowired
    private ClientLocationService clientLocationService;

    @PostMapping("/report")
    public ApiResponse<Long> report(@Valid @RequestBody ClientLocationReportDTO dto,
                                    @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.success("位置上报成功", clientLocationService.report(dto, userId));
    }

    @GetMapping
    public ApiResponse<ClientLocationPageResult> pageLocations(@RequestParam(defaultValue = "1") Long current,
                                                               @RequestParam(defaultValue = "10") Long size,
                                                               @RequestParam(required = false) String mac,
                                                               @RequestParam(required = false) Long userId,
                                                               @RequestHeader(value = "X-User-Id", required = false) Long currentUserId,
                                                               @RequestHeader(value = "X-User-Role", required = false) Integer currentRole,
                                                               @RequestParam(required = false)
                                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                               @RequestParam(required = false)
                                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        if (!Integer.valueOf(1).equals(currentRole)) {
            userId = currentUserId;
        }
        return ApiResponse.success(clientLocationService.pageLocations(current, size, mac, userId, startTime, endTime));
    }
}
