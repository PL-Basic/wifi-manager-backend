package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.device.TrafficEvaluationRequest;
import com.plagod.vo.device.TrafficEvaluationResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "monitor-service")
public interface MonitorServiceClient {

    @PostMapping("/internal/monitor/evaluate")
    ApiResponse<TrafficEvaluationResult> evaluate(@RequestHeader("X-Internal-Token") String internalToken,
                                                  @RequestBody TrafficEvaluationRequest request);
}