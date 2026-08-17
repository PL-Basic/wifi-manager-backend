package com.plagod.support;

import com.plagod.exception.ApiErrorKey;

public class SupportReviewPortException extends RuntimeException {

    private final String errorKey;

    public SupportReviewPortException(String errorKey) {
        super("Support review port 调用失败");
        this.errorKey = ApiErrorKey.requireValid(errorKey);
    }

    public String getErrorKey() {
        return errorKey;
    }
}
