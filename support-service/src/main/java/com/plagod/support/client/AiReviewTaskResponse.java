package com.plagod.support.client;

public class AiReviewTaskResponse {

    private Long reviewTaskId;

    public AiReviewTaskResponse() {
    }

    public AiReviewTaskResponse(Long reviewTaskId) {
        this.reviewTaskId = reviewTaskId;
    }

    public Long getReviewTaskId() {
        return reviewTaskId;
    }

    public void setReviewTaskId(Long reviewTaskId) {
        this.reviewTaskId = reviewTaskId;
    }
}
