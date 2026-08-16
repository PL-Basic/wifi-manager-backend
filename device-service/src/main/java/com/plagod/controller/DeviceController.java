package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.device.DeviceNodeCreateDTO;
import com.plagod.dto.device.DeviceNodeUpdateDTO;
import com.plagod.dto.device.KickDeviceDTO;
import com.plagod.dto.device.ManualBlockTrafficDTO;
import com.plagod.dto.device.ManualDisconnectMacDTO;
import com.plagod.dto.device.WifiConfigStageDTO;
import com.plagod.security.DeviceTenantAccessPolicy;
import com.plagod.service.DeviceCommandService;
import com.plagod.service.DeviceWifiConfigQueryService;
import com.plagod.service.DeviceWifiConfigService;
import com.plagod.service.ManualDeviceControlService;
import com.plagod.vo.device.DeviceCommandResult;
import com.plagod.vo.device.DeviceNodeVO;
import com.plagod.vo.device.DevicePageResult;
import com.plagod.vo.device.DeviceStatsVO;
import com.plagod.vo.device.WifiConfigTaskVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/internal/admin/devices")
public class DeviceController {

    @Autowired
    private DeviceCommandService deviceCommandService;
    @Autowired
    private ManualDeviceControlService manualDeviceControlService;
    @Autowired
    private DeviceWifiConfigService deviceWifiConfigService;
    @Autowired
    private DeviceWifiConfigQueryService deviceWifiConfigQueryService;
    @Autowired
    private DeviceTenantAccessPolicy tenantAccessPolicy;

    @PostMapping
    public ApiResponse<DeviceNodeVO> addDevice(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @Valid @RequestBody DeviceNodeCreateDTO request) {
        return ApiResponse.success(deviceCommandService.createDevice(tenantId, request));
    }

    @PostMapping("/{nodeId}/restore")
    public ApiResponse<DeviceNodeVO> restoreDevice(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable Long nodeId) {
        return ApiResponse.success(deviceCommandService.restoreDevice(tenantId, nodeId));
    }

    @PutMapping("/{nodeId}")
    public ApiResponse<DeviceNodeVO> updateDevice(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable Long nodeId,
            @Valid @RequestBody DeviceNodeUpdateDTO request) {
        return ApiResponse.success(deviceCommandService.updateDevice(tenantId, nodeId, request));
    }

    @DeleteMapping("/{nodeId}")
    public ApiResponse<Boolean> deleteDevice(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable Long nodeId) {
        deviceCommandService.deleteDevice(tenantId, nodeId);
        return ApiResponse.success(true);
    }

    @GetMapping("/stats")
    public ApiResponse<DeviceStatsVO> getDeviceStats(
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return ApiResponse.success(deviceCommandService.getDeviceStats(tenantId));
    }

    @GetMapping
    public ApiResponse<DevicePageResult> pageDevices(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(
                deviceCommandService.pageDevices(tenantId, current, size, keyword));
    }

    @GetMapping("/{nodeId}")
    public ApiResponse<DeviceNodeVO> getDevice(
            @PathVariable Long nodeId,
            HttpServletRequest servletRequest) {
        Long tenantId = tenantAccessPolicy.requireAdminTenantId(servletRequest);
        return ApiResponse.success(deviceCommandService.getDevice(tenantId, nodeId));
    }

    @PostMapping("/{deviceCode}/allow")
    public ApiResponse<DeviceNodeVO> allowDevice(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable String deviceCode) {
        return ApiResponse.success(deviceCommandService.allowDevice(tenantId, deviceCode));
    }

    @PostMapping("/{deviceCode}/kick")
    public ApiResponse<DeviceCommandResult> kickDevice(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable String deviceCode,
            @RequestBody(required = false) KickDeviceDTO request) {
        return ApiResponse.success(
                deviceCommandService.kickDevice(tenantId, deviceCode, request));
    }

    @PostMapping("/{deviceCode}/disconnect-mac")
    public ApiResponse<DeviceCommandResult> disconnectMac(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable String deviceCode,
            @Valid @RequestBody ManualDisconnectMacDTO request,
            @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole) {
        return ApiResponse.success(
                manualDeviceControlService.disconnectMac(
                        tenantId, deviceCode, request, operatorRole));
    }

    @PostMapping("/{deviceCode}/block-traffic")
    public ApiResponse<DeviceCommandResult> blockTraffic(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable String deviceCode,
            @Valid @RequestBody ManualBlockTrafficDTO request,
            @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole) {
        return ApiResponse.success(
                manualDeviceControlService.blockTraffic(
                        tenantId, deviceCode, request, operatorRole));
    }

    @PostMapping("/{deviceCode}/wifi-config/candidate")
    public ApiResponse<WifiConfigTaskVO> stageWifiCandidate(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable String deviceCode,
            @Valid @RequestBody WifiConfigStageDTO request) {
        return ApiResponse.success(
                deviceWifiConfigService.stageCandidate(tenantId, deviceCode, request));
    }

    @GetMapping("/{deviceCode}/wifi-config/{requestId}")
    public ApiResponse<WifiConfigTaskVO> getWifiConfigTask(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable String deviceCode,
            @PathVariable String requestId) {
        return ApiResponse.success(
                deviceWifiConfigQueryService.getTask(tenantId, deviceCode, requestId));
    }

    @GetMapping("/{deviceCode}/wifi-config/latest")
    public ApiResponse<WifiConfigTaskVO> getLatestWifiConfigTask(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable String deviceCode) {
        return ApiResponse.success(
                deviceWifiConfigQueryService.getLatestTask(tenantId, deviceCode));
    }
}
