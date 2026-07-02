package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.device.*;
import com.plagod.vo.device.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@FeignClient(name = "device-service")
public interface DeviceServiceClient {


    @PostMapping("/devices")
    ApiResponse<DeviceNodeVO> addDevice(@Valid @RequestBody DeviceNodeCreateDTO deviceNodeCreateDTO);

    @PostMapping("/devices/{nodeId}/restore")
    ApiResponse<DeviceNodeVO> restoreDevice(@PathVariable("nodeId") Long nodeId);

    @PutMapping("/devices/{nodeId}")
    ApiResponse<DeviceNodeVO> updateDevice(@PathVariable("nodeId") Long nodeId,@Valid @RequestBody DeviceNodeUpdateDTO deviceNodeUpdateDTO);

    @DeleteMapping("/devices/{nodeId}")
    ApiResponse<Boolean> deleteDevice(@PathVariable("nodeId") Long nodeId);

    @GetMapping("/devices")
    ApiResponse<DevicePageResult> pageDevices(@RequestParam("current") Long current,
                                              @RequestParam("size") Long size,
                                              @RequestParam(value = "keyword", required = false) String keyword);

    @GetMapping("/devices/{nodeId}")
    ApiResponse<DeviceNodeVO> getDevice(@PathVariable("nodeId") Long nodeId);

    @PostMapping("/devices/{deviceCode}/allow")
    ApiResponse<DeviceCommandResult> allowDevice(@PathVariable("deviceCode") String deviceCode);

    @PostMapping("/devices/{deviceCode}/kick")
    ApiResponse<DeviceCommandResult> kickDevice(@PathVariable("deviceCode") String deviceCode, @RequestBody KickDeviceDTO deviceKickDTO);

    @GetMapping("/blacklist")
    ApiResponse<MacBlacklistPageResult> pageBlacklist(@RequestParam("current") Long current,
                                                      @RequestParam("size") Long size,
                                                      @RequestParam(value = "keyword", required = false) String keyword);

    @PostMapping("/blacklist")
    ApiResponse<Void> addBlacklist(@RequestBody MacBlacklistCreateDTO macBlacklistCreateDTO);

    @DeleteMapping("/blacklist/{mac}")
    ApiResponse<Void> removeBlacklist(@PathVariable("mac") String mac);

    @GetMapping("/devices/stats")
    ApiResponse<DeviceStatsVO> getDeviceStats();

    @GetMapping("/sessions")
    ApiResponse<SessionPageResult> pageSessions(@RequestParam("current") Long current,
                                                @RequestParam("size") Long size,
                                                @RequestParam(value = "mac", required = false) String mac,
                                                @RequestParam(value = "nodeId", required = false) Long nodeId,
                                                @RequestParam(value = "userId", required = false) Long userId,
                                                @RequestParam(value = "status", required = false) Integer status);

    @GetMapping("/traffic")
    ApiResponse<TrafficPageResult> pageTraffic(@RequestParam("current") Long current,
                                    @RequestParam("size") Long size,
                                    @RequestParam(value = "mac", required = false) String mac,
                                    @RequestParam(value = "sessionId", required = false) Long sessionId,
                                    @RequestParam(value = "dstIp", required = false) String dstIp,
                                    @RequestParam(value = "startTime", required = false) String startTime,
                                    @RequestParam(value = "endTime", required = false) String endTime);
}
