package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.vo.device.LocationSessionContextVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "device-service", contextId = "deviceLocationSessionClient")
public interface DeviceLocationSessionClient {

    @GetMapping("/internal/location-sessions/{sessionId}")
    ApiResponse<LocationSessionContextVO> getLocationContext(@PathVariable("sessionId") Long sessionId,
                                                             @RequestHeader("X-User-Id") Long userId,
                                                             @RequestHeader("X-Internal-Token") String internalToken);
}