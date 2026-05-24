package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.TrafficEvaluationRequest;
import com.plagod.dto.TrafficEvaluationResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "monitor-service")
public interface MonitorServiceClient {

    @PostMapping("/monitor/evaluate")
    ApiResponse<TrafficEvaluationResult> evaluate(@RequestBody TrafficEvaluationRequest request);
}
