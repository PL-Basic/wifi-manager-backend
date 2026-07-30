package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.vo.device.SignalAnalyticsSourceVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "device-service", contextId = "deviceSignalAnalyticsClient")
public interface DeviceSignalAnalyticsClient {

    @GetMapping("/internal/analytics/signals")
    ApiResponse<SignalAnalyticsSourceVO> querySignals(@RequestParam("nodeId") Long nodeId,
                                                      @RequestParam("mac") String mac,
                                                      @RequestParam("startTime") String startTime,
                                                      @RequestParam("endTime") String endTime,
                                                      @RequestParam("sampleLimit") Integer sampleLimit,
                                                      @RequestParam("bucketMinutes") Integer bucketMinutes,
                                                      @RequestHeader("X-Internal-Token") String internalToken);
}