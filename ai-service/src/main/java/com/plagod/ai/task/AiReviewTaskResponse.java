package com.plagod.ai.task;

public final class AiReviewTaskResponse {

    private final Long reviewTaskId;

    public AiReviewTaskResponse(Long reviewTaskId) {
        this.reviewTaskId = reviewTaskId;
    }

    public Long getReviewTaskId() {
        return reviewTaskId;
    }
}
