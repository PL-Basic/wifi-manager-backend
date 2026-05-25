package com.plagod.controller;

import com.plagod.client.MonitorServiceClient;
import com.plagod.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/alerts")
public class AdminAlertController {

    @Autowired
    private MonitorServiceClient monitorServiceClient;

    @GetMapping
    public ApiResponse<Object> pageAlerts(@RequestParam(defaultValue = "1") Long current,
                                          @RequestParam(defaultValue = "10") Long size,
                                          @RequestParam(required = false) Integer level,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(required = false) String mac,
                                          @RequestParam(required = false) String startTime,
                                          @RequestParam(required = false) String endTime) {
        return monitorServiceClient.pageAlerts(current, size, level, status, mac, startTime, endTime);
    }

    @GetMapping("/{id}")
    public ApiResponse<Object> getAlert(@PathVariable Long id) {
        return monitorServiceClient.getAlert(id);
    }

    @PatchMapping("/{id}/handle")
    public ApiResponse<Void> handle(@PathVariable Long id, @RequestParam Long handleUserId) {
        return monitorServiceClient.handleAlert(id, handleUserId);
    }
}
