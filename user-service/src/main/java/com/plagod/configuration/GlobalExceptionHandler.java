package com.plagod.configuration;

import com.plagod.web.ApiErrorResponseFactory;
import com.plagod.web.ServletApiExceptionHandlerSupport;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler
        extends ServletApiExceptionHandlerSupport {

    public GlobalExceptionHandler(
            ApiErrorResponseFactory responseFactory) {
        super(responseFactory);
    }
}
