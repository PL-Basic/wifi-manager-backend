package com.plagod.controller;

import com.plagod.client.DeviceServiceClient;
import com.plagod.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/traffic")
public class AdminTrafficController {

    @Autowired
    private DeviceServiceClient deviceServiceClient;

    @GetMapping
    public ApiResponse<Object> pageTraffic(@RequestParam(defaultValue = "1") Long current,
                                           @RequestParam(defaultValue = "10") Long size,
                                           @RequestParam(required = false) String mac,
                                           @RequestParam(required = false) Long sessionId,
                                           @RequestParam(required = false) String dstIp,
                                           @RequestParam(required = false) String startTime,
                                           @RequestParam(required = false) String endTime) {
        return deviceServiceClient.pageTraffic(current, size, mac, sessionId, dstIp, startTime, endTime);
    }
}
