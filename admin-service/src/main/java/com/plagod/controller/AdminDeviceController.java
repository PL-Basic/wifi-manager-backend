package com.plagod.controller;

import com.plagod.client.DeviceServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.device.*;
import com.plagod.vo.device.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/admin/devices")
public class AdminDeviceController {

    @Autowired
    private DeviceServiceClient deviceServiceClient;

    @PostMapping
    public ApiResponse<DeviceNodeVO> addDevice(@Valid @RequestBody DeviceNodeCreateDTO deviceNodeCreateDTO) {
        return deviceServiceClient.addDevice(deviceNodeCreateDTO);
    }

    @PostMapping("/{nodeId}/restore")
    public ApiResponse<DeviceNodeVO> restoreDevice(@PathVariable Long nodeId) {
        return deviceServiceClient.restoreDevice(nodeId);
    }

    @PutMapping("/{nodeId}")
    public ApiResponse<DeviceNodeVO> updateDevice(@PathVariable Long nodeId,@Valid @RequestBody DeviceNodeUpdateDTO deviceNodeUpdateDTO) {
        return deviceServiceClient.updateDevice(nodeId, deviceNodeUpdateDTO);
    }

    @DeleteMapping("/{nodeId}")
    public ApiResponse<Boolean> deleteDevice(@PathVariable Long nodeId) {
        return deviceServiceClient.deleteDevice(nodeId);
    }

    @GetMapping
    public ApiResponse<DevicePageResult> pageDevices(@RequestParam(defaultValue = "1") Long current,
                                                     @RequestParam(defaultValue = "10") Long size,
                                                     @RequestParam(required = false) String keyword) {
        return deviceServiceClient.pageDevices(current, size, keyword);
    }

    @GetMapping("/stats")
    public ApiResponse<DeviceStatsVO> getDeviceStats() {
        return deviceServiceClient.getDeviceStats();
    }

    @GetMapping("/{nodeId}")
    public ApiResponse<DeviceNodeVO> getDevice(@PathVariable Long nodeId) {
        return deviceServiceClient.getDevice(nodeId);
    }

    @PostMapping("/{deviceCode}/allow")
    public ApiResponse<DeviceCommandResult> allowDevice(@PathVariable String deviceCode) {
        return deviceServiceClient.allowDevice(deviceCode);
    }

    @PostMapping("/{deviceCode}/kick")
    public ApiResponse<DeviceCommandResult> kickDevice(@PathVariable String deviceCode,
                                                       @RequestBody(required = false)KickDeviceDTO kickDeviceDTO) {
        return deviceServiceClient.kickDevice(deviceCode, kickDeviceDTO);
    }

    @GetMapping("/blacklist")
    public ApiResponse<MacBlacklistPageResult> pageBlacklist(@RequestParam(defaultValue = "1") Long current,
                                                             @RequestParam(defaultValue = "10") Long size,
                                                             @RequestParam(required = false) String keyword) {
        return deviceServiceClient.pageBlacklist(current, size, keyword);
    }

    @PostMapping("/blacklist")
    public ApiResponse<Void> addBlacklist(@RequestBody MacBlacklistCreateDTO macBlacklistCreateDTO) {
        return deviceServiceClient.addBlacklist(macBlacklistCreateDTO);
    }

    @DeleteMapping("/blacklist/{mac}")
    public ApiResponse<Void> removeBlacklist(@PathVariable String mac) {
        return deviceServiceClient.removeBlacklist(mac);
    }
}
