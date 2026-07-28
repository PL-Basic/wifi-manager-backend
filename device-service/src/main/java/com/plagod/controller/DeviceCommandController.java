package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.DeviceCommandQueryService;
import com.plagod.vo.device.DeviceCommandPageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/device-commands")
public class DeviceCommandController {

    @Autowired
    private DeviceCommandQueryService deviceCommandQueryService;

    @GetMapping
    public ApiResponse<DeviceCommandPageResult> pageCommands(@RequestParam(defaultValue = "1") Long current,
                                                             @RequestParam(defaultValue = "10") Long size,
                                                             @RequestParam(required = false) String requestId,
                                                             @RequestParam(required = false) String deviceCode,
                                                             @RequestParam(required = false) String commandType,
                                                             @RequestParam(required = false) String purpose,
                                                             @RequestParam(required = false) Integer status,
                                                             @RequestParam(required = false) Long sessionId,
                                                             @RequestParam(required = false) String mac) {

        return ApiResponse.success(deviceCommandQueryService.pageCommands(current, size, requestId, deviceCode, commandType, purpose, status, sessionId, mac));
    }
}