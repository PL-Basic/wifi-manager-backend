package com.plagod.exception;

public class VerificationDeliveryException extends RuntimeException {

    public VerificationDeliveryException(String message) {
        super(message);
    }

    public VerificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}