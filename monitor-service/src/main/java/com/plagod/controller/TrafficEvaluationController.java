package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.device.TrafficEvaluationRequest;
import com.plagod.vo.device.TrafficEvaluationResult;
import com.plagod.service.TrafficEvaluationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/monitor")
public class TrafficEvaluationController {

    @Autowired
    private TrafficEvaluationService trafficEvaluationService;

    @PostMapping("/evaluate")
    public ApiResponse<TrafficEvaluationResult> evaluate(@RequestBody TrafficEvaluationRequest request) {
        return ApiResponse.success(trafficEvaluationService.evaluate(request));
    }
}
