package com.plagod.controller;

import com.plagod.client.DeviceServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.vo.device.ClientSignalPageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/client-signals")
public class AdminClientSignalController {

    @Autowired
    private DeviceServiceClient deviceServiceClient;

    @GetMapping
    public ApiResponse<ClientSignalPageResult> pageClientSignals(@RequestParam(defaultValue = "1") Long current,
                                                                 @RequestParam(defaultValue = "10") Long size,
                                                                 @RequestParam(required = false) String deviceCode,
                                                                 @RequestParam(required = false) Long nodeId,
                                                                 @RequestParam(required = false) String mac,
                                                                 @RequestParam(required = false) Long sessionId,
                                                                 @RequestParam(required = false) String state,
                                                                 @RequestParam(required = false) String startTime,
                                                                 @RequestParam(required = false) String endTime) {
        return deviceServiceClient.pageClientSignals(current, size, deviceCode, nodeId, mac, sessionId, state, startTime, endTime);
    }
}