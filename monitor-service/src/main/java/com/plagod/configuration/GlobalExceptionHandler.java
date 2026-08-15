package com.plagod.configuration;

import com.plagod.web.ApiErrorResponseFactory;
import com.plagod.web.ServletApiExceptionHandlerSupport;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Monitor 只注册一个 Advice，并复用共享 HTTP 错误与 requestId 契约。
 */
@RestControllerAdvice
public class GlobalExceptionHandler
        extends ServletApiExceptionHandlerSupport {

    public GlobalExceptionHandler(
            ApiErrorResponseFactory responseFactory) {
        super(responseFactory);
    }
}
