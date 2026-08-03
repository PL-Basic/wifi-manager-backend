package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.device.*;
import com.plagod.vo.device.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@FeignClient(name = "device-service")
public interface DeviceServiceClient {


    @PostMapping("/internal/admin/devices")
    ApiResponse<DeviceNodeVO> addDevice(@Valid @RequestBody DeviceNodeCreateDTO deviceNodeCreateDTO);

    @PostMapping("/internal/admin/devices/{nodeId}/restore")
    ApiResponse<DeviceNodeVO> restoreDevice(@PathVariable("nodeId") Long nodeId);

    @PutMapping("/internal/admin/devices/{nodeId}")
    ApiResponse<DeviceNodeVO> updateDevice(@PathVariable("nodeId") Long nodeId,@Valid @RequestBody DeviceNodeUpdateDTO deviceNodeUpdateDTO);

    @DeleteMapping("/internal/admin/devices/{nodeId}")
    ApiResponse<Boolean> deleteDevice(@PathVariable("nodeId") Long nodeId);

    @GetMapping("/internal/admin/devices")
    ApiResponse<DevicePageResult> pageDevices(@RequestParam("current") Long current,
                                              @RequestParam("size") Long size,
                                              @RequestParam(value = "keyword", required = false) String keyword);

    @GetMapping("/internal/admin/devices/{nodeId}")
    ApiResponse<DeviceNodeVO> getDevice(@PathVariable("nodeId") Long nodeId);

    @PostMapping("/internal/admin/devices/{deviceCode}/allow")
    ApiResponse<DeviceNodeVO> allowDevice(@PathVariable("deviceCode") String deviceCode);

    @PostMapping("/internal/admin/devices/{deviceCode}/kick")
    ApiResponse<DeviceCommandResult> kickDevice(@PathVariable("deviceCode") String deviceCode,
                                                @RequestBody(required = false) KickDeviceDTO deviceKickDTO);

    @GetMapping("/internal/admin/devices/stats")
    ApiResponse<DeviceStatsVO> getDeviceStats();

    @GetMapping("/internal/admin/blacklist")
    ApiResponse<MacBlacklistPageResult> pageBlacklist(@RequestParam("current") Long current,
                                                      @RequestParam("size") Long size,
                                                      @RequestParam(value = "keyword", required = false) String keyword);

    @PostMapping("/internal/admin/blacklist")
    ApiResponse<Void> addBlacklist(@RequestBody MacBlacklistCreateDTO macBlacklistCreateDTO);

    @DeleteMapping("/internal/admin/blacklist/{mac}")
    ApiResponse<Void> removeBlacklist(@PathVariable("mac") String mac);


    @GetMapping("/internal/admin/sessions")
    ApiResponse<SessionPageResult> pageSessions(@RequestParam("current") Long current,
                                                @RequestParam("size") Long size,
                                                @RequestParam(value = "mac", required = false) String mac,
                                                @RequestParam(value = "nodeId", required = false) Long nodeId,
                                                @RequestParam(value = "userId", required = false) Long userId,
                                                @RequestParam(value = "status", required = false) Integer status);

    @PostMapping("/internal/admin/sessions/{sessionId}/revoke")
    ApiResponse<SessionRecordVO> adminRevokeSession(@PathVariable("sessionId") Long sessionId,
                                                    @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                                    @RequestHeader(value = "X-User-Name", required = false) String operatorName,
                                                    @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole);

    @GetMapping("/internal/admin/traffic")
    ApiResponse<TrafficPageResult> pageTraffic(@RequestParam("current") Long current,
                                               @RequestParam("size") Long size,
                                               @RequestParam(value = "mac", required = false) String mac,
                                               @RequestParam(value = "sessionId", required = false) Long sessionId,
                                               @RequestParam(value = "dstIp", required = false) String dstIp,
                                               @RequestParam(value = "startTime", required = false) String startTime,
                                               @RequestParam(value = "endTime", required = false) String endTime);


    @GetMapping("/internal/admin/client-signals")
    ApiResponse<ClientSignalPageResult> pageClientSignals(@RequestParam("current") Long current,
                                                          @RequestParam("size") Long size,
                                                          @RequestParam(value = "deviceCode", required = false) String deviceCode,
                                                          @RequestParam(value = "nodeId", required = false) Long nodeId,
                                                          @RequestParam(value = "mac", required = false) String mac,
                                                          @RequestParam(value = "sessionId", required = false) Long sessionId,
                                                          @RequestParam(value = "state", required = false) String state,
                                                          @RequestParam(value = "startTime", required = false) String startTime,
                                                          @RequestParam(value = "endTime", required = false) String endTime);


    @PostMapping("/internal/admin/devices/{deviceCode}/disconnect-mac")
    ApiResponse<DeviceCommandResult> disconnectMac(@PathVariable("deviceCode") String deviceCode,
                                                   @Valid @RequestBody ManualDisconnectMacDTO dto,
                                                   @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                                   @RequestHeader(value = "X-User-Name", required = false) String operatorName,
                                                   @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole);

    @PostMapping("/internal/admin/devices/{deviceCode}/block-traffic")
    ApiResponse<DeviceCommandResult> blockTraffic(@PathVariable("deviceCode") String deviceCode,
                                                  @Valid @RequestBody ManualBlockTrafficDTO dto,
                                                  @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                                  @RequestHeader(value = "X-User-Name", required = false) String operatorName,
                                                  @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole);

    @GetMapping("/internal/admin/device-commands")
    ApiResponse<DeviceCommandPageResult> pageDeviceCommands(@RequestParam("current") Long current,
                                                            @RequestParam("size") Long size,
                                                            @RequestParam(value = "requestId", required = false) String requestId,
                                                            @RequestParam(value = "deviceCode", required = false) String deviceCode,
                                                            @RequestParam(value = "commandType", required = false) String commandType,
                                                            @RequestParam(value = "purpose", required = false) String purpose,
                                                            @RequestParam(value = "status", required = false) Integer status,
                                                            @RequestParam(value = "sessionId", required = false) Long sessionId,
                                                            @RequestParam(value = "mac", required = false) String mac);

    @PostMapping("/internal/admin/devices/{deviceCode}/wifi-config/candidate")
    ApiResponse<WifiConfigTaskVO> stageWifiCandidate(@PathVariable("deviceCode") String deviceCode,
                                                     @Valid @RequestBody WifiConfigStageDTO stageDTO);

    @GetMapping("/internal/admin/devices/{deviceCode}/wifi-config/{requestId}")
    ApiResponse<WifiConfigTaskVO> getWifiConfigTask(@PathVariable("deviceCode") String deviceCode,
                                                    @PathVariable("requestId") String requestId);

    @GetMapping("/internal/admin/devices/{deviceCode}/wifi-config/latest")
    ApiResponse<WifiConfigTaskVO> getLatestWifiConfigTask(@PathVariable("deviceCode") String deviceCode);

}
