package com.plagod.service;

import com.plagod.dto.TrafficEvaluationRequest;
import com.plagod.dto.TrafficEvaluationResult;

public interface TrafficEvaluationService {
    TrafficEvaluationResult evaluate(TrafficEvaluationRequest request);
}
