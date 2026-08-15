package com.plagod.configuration;

import com.plagod.client.AdminDownstreamException;
import com.plagod.dto.ApiResponse;
import com.plagod.exception.ApiErrorKey;
import com.plagod.web.ApiErrorResponseFactory;
import com.plagod.web.ServletApiExceptionHandlerSupport;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler
        extends ServletApiExceptionHandlerSupport {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApiErrorResponseFactory responseFactory;

    public GlobalExceptionHandler(
            ApiErrorResponseFactory responseFactory) {
        super(responseFactory);
        this.responseFactory = responseFactory;
    }

    /**
     * BFF 下游错误必须保留业务状态，同时隐藏服务间认证细节。
     */
    @ExceptionHandler(AdminDownstreamException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleAdminDownstreamException(
            AdminDownstreamException exception) {
        return handleDownstreamStatus(
                exception.getDownstreamStatus(),
                exception.getRetryAfterSeconds(),
                exception.getClass().getSimpleName());
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleFeignException(FeignException exception) {
        return handleDownstreamStatus(
                exception.status(),
                null,
                exception.getClass().getSimpleName());
    }

    private ResponseEntity<ApiResponse<Void>> handleDownstreamStatus(
            int downstreamStatus,
            Long retryAfterSeconds,
            String exceptionType) {
        ResponseEntity<ApiResponse<Void>> response;
        if (downstreamStatus == HttpStatus.BAD_REQUEST.value()) {
            response = downstreamError(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorKey.VALIDATION_FAILED,
                    "下游服务拒绝了请求参数",
                    null);
        } else if (downstreamStatus
                == HttpStatus.NOT_FOUND.value()) {
            response = downstreamError(
                    HttpStatus.NOT_FOUND,
                    ApiErrorKey.RESOURCE_NOT_FOUND,
                    "资源不存在",
                    null);
        } else if (downstreamStatus
                == HttpStatus.CONFLICT.value()) {
            response = downstreamError(
                    HttpStatus.CONFLICT,
                    ApiErrorKey.RESOURCE_VERSION_CONFLICT,
                    "资源状态冲突",
                    null);
        } else if (downstreamStatus
                == HttpStatus.TOO_MANY_REQUESTS.value()) {
            response = downstreamError(
                    HttpStatus.TOO_MANY_REQUESTS,
                    ApiErrorKey.RATE_LIMITED,
                    "请求过于频繁，请稍后再试",
                    retryAfterSeconds);
        } else if (downstreamStatus
                == HttpStatus.UNAUTHORIZED.value()
                || downstreamStatus
                == HttpStatus.FORBIDDEN.value()) {
            response = downstreamError(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorKey.DEPENDENCY_UNAVAILABLE,
                    "服务间认证暂时不可用",
                    null);
        } else {
            response = downstreamError(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorKey.DEPENDENCY_UNAVAILABLE,
                    "下游服务暂时不可用",
                    null);
        }

        LOGGER.warn(
                "admin downstream request failed: "
                        + "requestId={}, status={}, exception={}",
                response.getBody() == null
                        ? null
                        : response.getBody().getRequestId(),
                downstreamStatus,
                exceptionType);
        return response;
    }

    private ResponseEntity<ApiResponse<Void>> downstreamError(
            HttpStatus status,
            ApiErrorKey errorKey,
            String message,
            Long retryAfterSeconds) {
        return responseFactory.error(
                status.value(),
                status.value(),
                errorKey.value(),
                message,
                null,
                retryAfterSeconds);
    }
}
