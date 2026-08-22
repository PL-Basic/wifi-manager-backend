package com.plagod.support.client;

import com.plagod.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ai-service", contextId = "supportAiReviewTaskClient")
public interface AiReviewTaskClient {

    @PostMapping("/internal/ai/review-tasks")
    ApiResponse<AiReviewTaskResponse> createReviewTask(
            @RequestBody AiReviewTaskRequest request);
}
