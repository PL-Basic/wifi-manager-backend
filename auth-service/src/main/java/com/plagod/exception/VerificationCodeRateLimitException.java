package com.plagod.exception;

public class VerificationCodeRateLimitException extends IllegalArgumentException {

    public VerificationCodeRateLimitException(String message) {
        super(message);
    }
}