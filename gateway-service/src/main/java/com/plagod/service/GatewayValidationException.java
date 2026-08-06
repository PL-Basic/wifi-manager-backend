package com.plagod.service;

import lombok.Getter;

@Getter
public class GatewayValidationException extends RuntimeException {

    private final int httpStatus;
    private final int code;

    public GatewayValidationException(int httpStatus, int code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }
}
