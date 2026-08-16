package com.plagod.security;

import com.plagod.exception.ApiErrorKey;

public final class TrustedRequestContextException extends RuntimeException {

    private final int httpStatus;
    private final ApiErrorKey errorKey;

    private TrustedRequestContextException(
            int httpStatus,
            ApiErrorKey errorKey,
            String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorKey = errorKey;
    }

    public static TrustedRequestContextException authentication(
            String message) {
        return new TrustedRequestContextException(
                401,
                ApiErrorKey.AUTHENTICATION_REQUIRED,
                message);
    }

    public static TrustedRequestContextException permission(
            String message) {
        return new TrustedRequestContextException(
                403,
                ApiErrorKey.PERMISSION_DENIED,
                message);
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public ApiErrorKey getErrorKey() {
        return errorKey;
    }
}
