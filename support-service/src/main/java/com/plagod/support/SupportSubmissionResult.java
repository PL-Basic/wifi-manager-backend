package com.plagod.support;

public final class SupportSubmissionResult {

    private final Long submissionId;
    private final String reviewRequestId;
    private final String status;
    private final boolean duplicate;

    public SupportSubmissionResult(
            Long submissionId,
            String reviewRequestId,
            String status,
            boolean duplicate) {
        this.submissionId = submissionId;
        this.reviewRequestId = reviewRequestId;
        this.status = status;
        this.duplicate = duplicate;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public String getReviewRequestId() {
        return reviewRequestId;
    }

    public String getStatus() {
        return status;
    }

    public boolean isDuplicate() {
        return duplicate;
    }
}
