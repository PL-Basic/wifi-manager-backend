package com.plagod.exception;

/**
 * 表示已经明确分类的 API 业务状态。
 * 不依赖 Spring HttpStatus，避免 common-api 引入 Web 框架依赖。
 */
public class ApiStatusException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int httpStatus;
    private final int code;
    private final Long retryAfterSeconds;

    public ApiStatusException(int httpStatus, int code, String message) {
        this(httpStatus, code, message, null);
    }

    public ApiStatusException(int httpStatus, int code, String message, Long retryAfterSeconds) {
        super(message);

        if (httpStatus < 400 || httpStatus > 599) {
            throw new IllegalArgumentException("HTTP 状态码必须在 400 到 599 之间");
        }

        this.httpStatus = httpStatus;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiStatusException notFound(String message) {
        return new ApiStatusException(404, 404, message);
    }

    public static ApiStatusException forbidden(String message) {
        return new ApiStatusException(403, 403, message);
    }

    public static ApiStatusException conflict(String message) {
        return new ApiStatusException(409, 409, message);
    }

    public static ApiStatusException tooManyRequests(String message,
                                                     long retryAfterSeconds) {
        return new ApiStatusException(429, 429, message, Math.max(1L, retryAfterSeconds));
    }

    public static ApiStatusException badGateway(String message) {
        return new ApiStatusException(502, 502, message);
    }

    public static ApiStatusException serviceUnavailable(String message) {
        return new ApiStatusException(503, 503, message);
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public int getCode() {
        return code;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
