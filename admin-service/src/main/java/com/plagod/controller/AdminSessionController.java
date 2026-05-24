package com.plagod.controller;

import com.plagod.client.DeviceServiceClient;
import com.plagod.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/sessions")
public class AdminSessionController {

    @Autowired
    private DeviceServiceClient deviceServiceClient;

    @GetMapping
    public ApiResponse<Object> pageSessions(@RequestParam(defaultValue = "1") Long current,
                                            @RequestParam(defaultValue = "10") Long size,
                                            @RequestParam(required = false) String mac,
                                            @RequestParam(required = false) Long nodeId,
                                            @RequestParam(required = false) Long userId,
                                            @RequestParam(required = false) Integer status) {
        return deviceServiceClient.pageSessions(current, size, mac, nodeId, userId, status);
    }
}
