package com.plagod.configuration;

import com.plagod.dto.ApiResponse;
import com.plagod.exception.ApiErrorKey;
import com.plagod.web.ApiErrorResponseFactory;
import com.plagod.web.RequestIdContext;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerContractTest {

    private static final String REQUEST_ID = "request-id-00000001";
    private static final String CANARY =
            "monitor-handler-canary-secret";

    @Test
    void delegatesUnknownAndIllegalArgumentErrorsToSafeSharedSupport() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                new ApiErrorResponseFactory());
        MDC.put(RequestIdContext.MDC_KEY, REQUEST_ID);
        try {
            ResponseEntity<ApiResponse<Void>> unknown =
                    handler.handleUnexpectedException(
                            new IllegalStateException(CANARY));

            assertEquals(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    unknown.getStatusCode());
            assertEquals(
                    ApiErrorKey.INTERNAL_ERROR.value(),
                    unknown.getBody().getErrorKey());
            assertEquals(REQUEST_ID, unknown.getBody().getRequestId());
            assertEquals(
                    REQUEST_ID,
                    unknown.getHeaders().getFirst("X-Request-Id"));
            assertFalse(unknown.getBody().getMessage().contains(CANARY));

            ResponseEntity<ApiResponse<Void>> invalid =
                    handler.handleIllegalArgumentException(
                            new IllegalArgumentException(CANARY));

            assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
            assertEquals(
                    ApiErrorKey.VALIDATION_FAILED.value(),
                    invalid.getBody().getErrorKey());
            assertFalse(invalid.getBody().getMessage().contains(CANARY));
        } finally {
            MDC.remove(RequestIdContext.MDC_KEY);
        }
    }
}
