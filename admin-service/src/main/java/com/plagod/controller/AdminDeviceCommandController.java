package com.plagod.controller;

import com.plagod.client.DeviceServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.vo.device.DeviceCommandPageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/device-commands")
public class AdminDeviceCommandController {

    @Autowired
    private DeviceServiceClient deviceServiceClient;

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

        return deviceServiceClient.pageDeviceCommands(current, size, requestId, deviceCode, commandType, purpose, status, sessionId, mac);
    }
}