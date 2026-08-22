package com.plagod.support;

public final class SupportReviewReceipt {

    private final Long reviewTaskId;

    public SupportReviewReceipt(Long reviewTaskId) {
        if (reviewTaskId == null || reviewTaskId <= 0L) {
            throw new IllegalArgumentException("reviewTaskId 必须为正数");
        }
        this.reviewTaskId = reviewTaskId;
    }

    public Long getReviewTaskId() {
        return reviewTaskId;
    }
}
