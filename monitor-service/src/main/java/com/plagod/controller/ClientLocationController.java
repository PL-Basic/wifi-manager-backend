package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.ClientLocationReportDTO;
import com.plagod.service.ClientLocationService;
import com.plagod.vo.monitor.ClientLocationPageResult;
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

    @PostMapping("/report")
    public ApiResponse<Long> report(@Valid @RequestBody ClientLocationReportDTO dto,
                                    @RequestHeader("X-User-Id") Long userId) {

        return ApiResponse.success("位置上报成功", clientLocationService.report(dto, userId));
    }

    @GetMapping
    public ApiResponse<ClientLocationPageResult> pageOwnedLocations(@RequestParam(defaultValue = "1") Long current,
                                                                    @RequestParam(defaultValue = "10") Long size,
                                                                    @RequestParam(required = false) String mac,
                                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                                                    @RequestHeader("X-User-Id") Long userId) {

        // 不再接受 userId 查询参数，强制使用 Gateway 身份。
        return ApiResponse.success(clientLocationService.pageOwnedLocations(userId, current, size, mac, startTime, endTime));
    }
}