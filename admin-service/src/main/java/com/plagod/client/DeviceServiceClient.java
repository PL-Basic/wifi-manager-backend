package com.plagod.client;

import com.plagod.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "device-service")
public interface DeviceServiceClient {

    @GetMapping("/devices/{nodeId}")
    ApiResponse<Object> getDevice(@PathVariable("nodeId") Long nodeId);

    @PostMapping("/devices/{deviceCode}/allow")
    ApiResponse<Object> allowDevice(@PathVariable("deviceCode") String deviceCode);

    @PostMapping("/devices/{deviceCode}/kick")
    ApiResponse<Object> kickDevice(@PathVariable("deviceCode") String deviceCode, @RequestBody Map<String, Object> body);

    @PostMapping("/blacklist")
    ApiResponse<Void> addBlacklist(@RequestBody Map<String, Object> body);

    @DeleteMapping("/blacklist/{mac}")
    ApiResponse<Void> removeBlacklist(@PathVariable("mac") String mac);
}
