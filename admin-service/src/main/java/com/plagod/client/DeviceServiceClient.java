package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.DeviceStatsVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "device-service")
public interface DeviceServiceClient {

    @GetMapping("/devices")
    ApiResponse<Object> pageDevices(@RequestParam("current") Long current,
                                    @RequestParam("size") Long size,
                                    @RequestParam(value = "keyword", required = false) String keyword);

    @GetMapping("/devices/{nodeId}")
    ApiResponse<Object> getDevice(@PathVariable("nodeId") Long nodeId);

    @PostMapping("/devices/{deviceCode}/allow")
    ApiResponse<Object> allowDevice(@PathVariable("deviceCode") String deviceCode);

    @PostMapping("/devices/{deviceCode}/kick")
    ApiResponse<Object> kickDevice(@PathVariable("deviceCode") String deviceCode, @RequestBody Map<String, Object> body);

    @GetMapping("/blacklist")
    ApiResponse<Object> pageBlacklist(@RequestParam("current") Long current,
                                      @RequestParam("size") Long size,
                                      @RequestParam(value = "keyword", required = false) String keyword);

    @PostMapping("/blacklist")
    ApiResponse<Void> addBlacklist(@RequestBody Map<String, Object> body);

    @DeleteMapping("/blacklist/{mac}")
    ApiResponse<Void> removeBlacklist(@PathVariable("mac") String mac);

    @GetMapping("/devices/stats")
    ApiResponse<DeviceStatsVO> getDeviceStats();

    @GetMapping("/sessions")
    ApiResponse<Object> pageSessions(@RequestParam("current") Long current,
                                     @RequestParam("size") Long size,
                                     @RequestParam(value = "mac", required = false) String mac,
                                     @RequestParam(value = "nodeId", required = false) Long nodeId,
                                     @RequestParam(value = "userId", required = false) Long userId,
                                     @RequestParam(value = "status", required = false) Integer status);

    @GetMapping("/traffic")
    ApiResponse<Object> pageTraffic(@RequestParam("current") Long current,
                                    @RequestParam("size") Long size,
                                    @RequestParam(value = "mac", required = false) String mac,
                                    @RequestParam(value = "sessionId", required = false) Long sessionId,
                                    @RequestParam(value = "dstIp", required = false) String dstIp,
                                    @RequestParam(value = "startTime", required = false) String startTime,
                                    @RequestParam(value = "endTime", required = false) String endTime);
}
