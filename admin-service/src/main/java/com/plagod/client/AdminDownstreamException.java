package com.plagod.client;

/**
 * 保存 BFF 可安全消费的下游状态元数据，不携带响应正文。
 */
public class AdminDownstreamException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int downstreamStatus;
    private final Long retryAfterSeconds;

    public AdminDownstreamException(
            int downstreamStatus,
            Long retryAfterSeconds) {
        super("admin downstream request failed");
        this.downstreamStatus = downstreamStatus;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getDownstreamStatus() {
        return downstreamStatus;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
