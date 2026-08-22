package com.plagod.support;

public final class SupportReviewClaim {

    private final Long eventId;
    private final String workerId;
    private final int retryCount;
    private final int submissionVersion;
    private final SupportReviewRequest reviewRequest;

    public SupportReviewClaim(
            Long eventId,
            String workerId,
            int retryCount,
            int submissionVersion,
            SupportReviewRequest reviewRequest) {
        this.eventId = eventId;
        this.workerId = workerId;
        this.retryCount = retryCount;
        this.submissionVersion = submissionVersion;
        this.reviewRequest = reviewRequest;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getSubmissionVersion() {
        return submissionVersion;
    }

    public SupportReviewRequest getReviewRequest() {
        return reviewRequest;
    }
}
