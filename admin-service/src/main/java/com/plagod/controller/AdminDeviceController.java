package com.plagod.controller;

import com.plagod.client.DeviceServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.device.*;
import com.plagod.exception.ApiStatusException;
import com.plagod.vo.device.*;
import feign.FeignException;
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
    public ApiResponse<DeviceNodeVO> allowDevice(@PathVariable String deviceCode) {
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

    @PostMapping("/{deviceCode}/disconnect-mac")
    public ApiResponse<DeviceCommandResult> disconnectMac(@PathVariable String deviceCode,
                                                          @Valid @RequestBody ManualDisconnectMacDTO dto,
                                                          @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                                          @RequestHeader(value = "X-User-Name", required = false) String operatorName,
                                                          @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole) {

        return deviceServiceClient.disconnectMac(deviceCode, dto, operatorId, operatorName, operatorRole);
    }

    @PostMapping("/{deviceCode}/block-traffic")
    public ApiResponse<DeviceCommandResult> blockTraffic(@PathVariable String deviceCode,
                                                         @Valid @RequestBody ManualBlockTrafficDTO dto,
                                                         @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                                         @RequestHeader(value = "X-User-Name", required = false) String operatorName,
                                                         @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole) {

        return deviceServiceClient.blockTraffic(deviceCode, dto, operatorId, operatorName, operatorRole);
    }

    @PostMapping("/{deviceCode}/wifi-config/candidate")
    public ApiResponse<WifiConfigTaskVO> stageWifiCandidate(@PathVariable String deviceCode,
                                                            @Valid @RequestBody WifiConfigStageDTO stageDTO) {

        try {
            return deviceServiceClient.stageWifiCandidate(deviceCode, stageDTO);
        } catch (FeignException exception) {

            int status = exception.status();

            if (status == 400) {
                throw new IllegalArgumentException("候选 WiFi 配置参数无效");
            }

            if (status == 404) {
                throw ApiStatusException.notFound("目标 ESP32 不存在或已经退役");
            }

            if (status == 409) {
                throw ApiStatusException.conflict("设备当前离线、心跳已经过期，或者配置任务状态已经变化，请刷新后重试");
            }

            if (status == 429) {
                throw ApiStatusException.tooManyRequests("设备配置请求过于频繁", 1L);
            }

            // 内部 401/403、连接失败和下游 5xx 都是服务端问题。
            throw ApiStatusException.serviceUnavailable("设备配置服务暂时不可用");
        }
    }

    @GetMapping("/{deviceCode}/wifi-config/{requestId}")
    public ApiResponse<WifiConfigTaskVO> getWifiConfigTask(@PathVariable String deviceCode,
                                                           @PathVariable String requestId) {

        return deviceServiceClient.getWifiConfigTask(deviceCode, requestId);
    }
}