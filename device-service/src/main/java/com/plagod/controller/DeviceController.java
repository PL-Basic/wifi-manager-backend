package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.DeviceCommandResult;
import com.plagod.dto.DeviceNodeVO;
import com.plagod.dto.DevicePageResult;
import com.plagod.dto.DeviceStatsVO;
import com.plagod.dto.KickDeviceDTO;
import com.plagod.service.DeviceCommandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/devices")
public class DeviceController {

    @Autowired
    private DeviceCommandService deviceCommandService;

    @GetMapping("/stats")
    public ApiResponse<DeviceStatsVO> getDeviceStats() {
        return ApiResponse.success(deviceCommandService.getDeviceStats());
    }

    @GetMapping
    public ApiResponse<DevicePageResult> pageDevices(@RequestParam(defaultValue = "1") Long current,
                                                     @RequestParam(defaultValue = "10") Long size,
                                                     @RequestParam(required = false) String keyword) {
        return ApiResponse.success(deviceCommandService.pageDevices(current, size, keyword));
    }

    @GetMapping("/{nodeId}")
    public ApiResponse<DeviceNodeVO> getDevice(@PathVariable Long nodeId) {
        return ApiResponse.success(deviceCommandService.getDevice(nodeId));
    }

    @PostMapping("/{deviceCode}/allow")
    public ApiResponse<DeviceCommandResult> allowDevice(@PathVariable String deviceCode) {
        return ApiResponse.success("允许设备接入成功", deviceCommandService.allowDevice(deviceCode));
    }

    @PostMapping("/{deviceCode}/kick")
    public ApiResponse<DeviceCommandResult> kickDevice(@PathVariable String deviceCode,
                                                       @RequestBody(required = false) KickDeviceDTO kickDeviceDTO) {
        return ApiResponse.success("踢出设备命令已下发", deviceCommandService.kickDevice(deviceCode, kickDeviceDTO));
    }
}
