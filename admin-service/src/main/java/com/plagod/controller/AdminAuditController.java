package com.plagod.controller;

import com.plagod.client.MonitorServiceClient;
import com.plagod.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/audits")
public class AdminAuditController {

    @Autowired
    private MonitorServiceClient monitorServiceClient;

    @GetMapping
    public ApiResponse<Object> pageAudits(@RequestParam(defaultValue = "1") Long current,
                                          @RequestParam(defaultValue = "10") Long size,
                                          @RequestParam(required = false) String action,
                                          @RequestParam(required = false) String operatorName,
                                          @RequestParam(required = false) String target,
                                          @RequestParam(required = false) String startTime,
                                          @RequestParam(required = false) String endTime) {
        return monitorServiceClient.pageAudits(current, size, action, operatorName, target, startTime, endTime);
    }
}
