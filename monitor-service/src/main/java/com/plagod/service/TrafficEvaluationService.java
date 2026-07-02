package com.plagod.service;

import com.plagod.dto.device.TrafficEvaluationRequest;
import com.plagod.vo.device.TrafficEvaluationResult;

public interface TrafficEvaluationService {
    TrafficEvaluationResult evaluate(TrafficEvaluationRequest request);
}
