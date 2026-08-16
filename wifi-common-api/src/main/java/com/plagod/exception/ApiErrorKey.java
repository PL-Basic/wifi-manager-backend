package com.plagod.exception;

import java.util.regex.Pattern;

/**
 * Demo 基础错误键注册表。领域错误键仍须使用同一格式并在对应阶段登记。
 */
public enum ApiErrorKey {

    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    AUTHENTICATION_REQUIRED,
    SESSION_EXPIRED,
    PERMISSION_DENIED,
    RESOURCE_NOT_FOUND,
    RESOURCE_VERSION_CONFLICT,
    IDEMPOTENCY_KEY_CONFLICT,
    RATE_LIMITED,
    DEPENDENCY_UNAVAILABLE,
    DEPENDENCY_PROTOCOL_INVALID,
    PAYMENT_CHANNEL_UNAVAILABLE,
    AI_PROVIDER_UNAVAILABLE,
    AI_RESPONSE_INVALID,
    INTERNAL_ERROR;

    private static final Pattern FORMAT =
            Pattern.compile("^[A-Z][A-Z0-9_]*$");

    public String value() {
        return name();
    }

    public static boolean isValid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    public static String requireValid(String value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException(
                    "errorKey 必须是非空 UPPER_SNAKE_CASE");
        }
        return value;
    }

    public static ApiErrorKey defaultForHttpStatus(int httpStatus) {
        switch (httpStatus) {
            case 400:
                return VALIDATION_FAILED;
            case 401:
                return AUTHENTICATION_REQUIRED;
            case 403:
                return PERMISSION_DENIED;
            case 404:
                return RESOURCE_NOT_FOUND;
            case 409:
                return RESOURCE_VERSION_CONFLICT;
            case 429:
                return RATE_LIMITED;
            case 502:
                return DEPENDENCY_PROTOCOL_INVALID;
            case 503:
                return DEPENDENCY_UNAVAILABLE;
            default:
                return INTERNAL_ERROR;
        }
    }
}
