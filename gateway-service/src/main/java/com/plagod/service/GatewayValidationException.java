package com.plagod.service;

import com.plagod.exception.ApiErrorKey;
import lombok.Getter;

@Getter
public class GatewayValidationException extends RuntimeException {

    private final int httpStatus;
    private final int code;
    private final String errorKey;

    public GatewayValidationException(int httpStatus, int code, String message) {
        this(
                httpStatus,
                code,
                ApiErrorKey.defaultForHttpStatus(httpStatus).value(),
                message);
    }

    public GatewayValidationException(
            int httpStatus,
            int code,
            String errorKey,
            String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.errorKey = ApiErrorKey.requireValid(errorKey);
    }
}
