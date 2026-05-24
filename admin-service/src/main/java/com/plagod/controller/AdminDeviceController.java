package com.plagod.controller;

import com.plagod.client.DeviceServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.DeviceStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/admin/devices")
public class AdminDeviceController {

    @Autowired
    private DeviceServiceClient deviceServiceClient;

    @GetMapping
    public ApiResponse<Object> pageDevices(@RequestParam(defaultValue = "1") Long current,
                                           @RequestParam(defaultValue = "10") Long size,
                                           @RequestParam(required = false) String keyword) {
        return deviceServiceClient.pageDevices(current, size, keyword);
    }

    @GetMapping("/stats")
    public ApiResponse<DeviceStatsVO> getDeviceStats() {
        return deviceServiceClient.getDeviceStats();
    }

    @GetMapping("/{nodeId}")
    public ApiResponse<Object> getDevice(@PathVariable Long nodeId) {
        return deviceServiceClient.getDevice(nodeId);
    }

    @PostMapping("/{deviceCode}/allow")
    public ApiResponse<Object> allowDevice(@PathVariable String deviceCode) {
        return deviceServiceClient.allowDevice(deviceCode);
    }

    @PostMapping("/{deviceCode}/kick")
    public ApiResponse<Object> kickDevice(@PathVariable String deviceCode,
                                          @RequestBody(required = false) Map<String, Object> body) {
        return deviceServiceClient.kickDevice(deviceCode, body == null ? Collections.emptyMap() : body);
    }

    @GetMapping("/blacklist")
    public ApiResponse<Object> pageBlacklist(@RequestParam(defaultValue = "1") Long current,
                                             @RequestParam(defaultValue = "10") Long size,
                                             @RequestParam(required = false) String keyword) {
        return deviceServiceClient.pageBlacklist(current, size, keyword);
    }

    @PostMapping("/blacklist")
    public ApiResponse<Void> addBlacklist(@RequestBody Map<String, Object> body) {
        return deviceServiceClient.addBlacklist(body);
    }

    @DeleteMapping("/blacklist/{mac}")
    public ApiResponse<Void> removeBlacklist(@PathVariable String mac) {
        return deviceServiceClient.removeBlacklist(mac);
    }
}
