package com.plagod.exception;

public class VerificationCodeRateLimitException extends IllegalArgumentException {

    private final long retryAfterSeconds;

    public VerificationCodeRateLimitException(String message) {
        this(message, 60L);
    }

    public VerificationCodeRateLimitException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}