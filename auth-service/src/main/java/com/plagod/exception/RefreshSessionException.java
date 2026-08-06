package com.plagod.exception;

import lombok.Getter;

@Getter
public class RefreshSessionException extends RuntimeException {

    private final int httpStatus;
    private final String code;

    public RefreshSessionException(int httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }
}
