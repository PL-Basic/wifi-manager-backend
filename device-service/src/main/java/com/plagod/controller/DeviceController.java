package com.plagod.controller;

import com.plagod.dto.*;
import com.plagod.vo.DeviceNodeVO;
import com.plagod.vo.DevicePageResult;
import com.plagod.service.DeviceCommandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/devices")
public class DeviceController {

    @Autowired
    private DeviceCommandService deviceCommandService;

    @PostMapping
    public ApiResponse<DeviceNodeVO> addDevice(@Valid @RequestBody DeviceNodeCreateDTO deviceNodeCreateDTO) {
        DeviceNodeVO deviceNodeVO = deviceCommandService.createDevice(deviceNodeCreateDTO);
        return ApiResponse.success(deviceNodeVO);
    }

    @PostMapping("/{nodeId}/restore")
    public ApiResponse<DeviceNodeVO> restoreDevice(@PathVariable Long nodeId) {
        DeviceNodeVO deviceNodeVO = deviceCommandService.restoreDevice(nodeId);
        return ApiResponse.success(deviceNodeVO);
    }

    @PutMapping("/{nodeId}")
    public ApiResponse<DeviceNodeVO> updateDevice(@PathVariable Long nodeId,
                                                  @Valid @RequestBody DeviceNodeUpdateDTO deviceNodeUpdateDTO) {
        DeviceNodeVO deviceNodeVO = deviceCommandService.updateDevice(nodeId, deviceNodeUpdateDTO);
        return ApiResponse.success(deviceNodeVO);
    }

    @DeleteMapping("/{nodeId}")
    public ApiResponse<Boolean> deleteDevice(@PathVariable Long nodeId) {
        deviceCommandService.deleteDevice(nodeId);
        return ApiResponse.success(true);
    }


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
