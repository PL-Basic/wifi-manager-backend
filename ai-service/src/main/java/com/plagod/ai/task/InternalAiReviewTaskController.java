package com.plagod.ai.task;

import com.plagod.dto.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/internal/ai/review-tasks")
public class InternalAiReviewTaskController {

    private final AiReviewTaskEntryService entryService;

    public InternalAiReviewTaskController(
            AiReviewTaskEntryService entryService) {
        this.entryService = Objects.requireNonNull(
                entryService,
                "entryService 不能为空");
    }

    @PostMapping
    public ApiResponse<AiReviewTaskResponse> create(
            @RequestBody AiReviewTaskRequest request) {
        AiReviewTaskEntryResult result =
                entryService.submit(request.toSubmission());
        return ApiResponse.success(new AiReviewTaskResponse(
                result.getTask().getReviewTaskId()));
    }
}
