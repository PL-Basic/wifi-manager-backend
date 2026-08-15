package com.plagod.configuration;

import com.plagod.dto.ApiResponse;
import com.plagod.exception.ApiErrorKey;
import com.plagod.exception.RefreshSessionException;
import com.plagod.exception.VerificationCodeRateLimitException;
import com.plagod.exception.VerificationDeliveryException;
import com.plagod.web.ApiErrorResponseFactory;
import com.plagod.web.ServletApiExceptionHandlerSupport;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler
        extends ServletApiExceptionHandlerSupport {

    private final ApiErrorResponseFactory responseFactory;

    public GlobalExceptionHandler(ApiErrorResponseFactory responseFactory) {
        super(responseFactory);
        this.responseFactory = responseFactory;
    }

    @ExceptionHandler(VerificationCodeRateLimitException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleVerificationCodeRateLimitException(
            VerificationCodeRateLimitException exception) {
        return responseFactory.error(
                429,
                429,
                ApiErrorKey.RATE_LIMITED.value(),
                exception.getMessage(),
                null,
                exception.getRetryAfterSeconds());
    }

    @ExceptionHandler(VerificationDeliveryException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleVerificationDeliveryException(
            VerificationDeliveryException exception) {
        return responseFactory.error(
                503,
                503,
                ApiErrorKey.DEPENDENCY_UNAVAILABLE.value(),
                exception.getMessage(),
                null,
                null);
    }

    @ExceptionHandler(RefreshSessionException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleRefreshSessionException(RefreshSessionException exception) {
        return responseFactory.error(
                exception.getHttpStatus(),
                exception.getHttpStatus(),
                exception.getCode(),
                exception.getMessage(),
                null,
                null);
    }
}
