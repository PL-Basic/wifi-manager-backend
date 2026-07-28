package com.plagod.controller;

import com.plagod.client.MonitorServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.vo.monitor.AlertEventPageResult;
import com.plagod.vo.monitor.AlertEventVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/alerts")
public class AdminAlertController {

    @Autowired
    private MonitorServiceClient monitorServiceClient;

    @GetMapping
    public ApiResponse<AlertEventPageResult> pageAlerts(@RequestParam(defaultValue = "1") Long current,
                                                        @RequestParam(defaultValue = "10") Long size,
                                                        @RequestParam(required = false) Integer level,
                                                        @RequestParam(required = false) Integer status,
                                                        @RequestParam(required = false) String mac,
                                                        @RequestParam(required = false) String startTime,
                                                        @RequestParam(required = false) String endTime) {
        return monitorServiceClient.pageAlerts(current, size, level, status, mac, startTime, endTime);
    }

    @GetMapping("/{id}")
    public ApiResponse<AlertEventVO> getAlert(@PathVariable Long id) {
        return monitorServiceClient.getAlert(id);
    }

    @PatchMapping("/{id}/handle")
    public ApiResponse<Void> handle(@PathVariable Long id,
                                    @RequestHeader("X-User-Id") Long handleUserId) {

        // 处理人只能来自 Gateway 注入的当前管理员身份。
        return monitorServiceClient.handleAlert(id, handleUserId);
    }
}
