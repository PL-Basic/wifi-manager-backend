package com.plagod.configuration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.plagod.dto.ApiResponse;
import com.plagod.exception.ApiErrorKey;
import com.plagod.request.RequestId;
import com.plagod.web.ApiErrorResponseFactory;
import com.plagod.web.RequestIdContext;
import com.plagod.web.ServletApiExceptionHandlerSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DeviceHttpSupportContractTest {

    private static final String REQUEST_ID = "request-id-00000001";
    private static final String CANARY_SECRET = "device-http-canary-secret";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void deviceAdviceUsesSharedRequestIdAndSafeUnknownErrorContract() {
        MDC.put(RequestIdContext.MDC_KEY, REQUEST_ID);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                new ApiErrorResponseFactory());

        Logger logger = (Logger) LoggerFactory.getLogger(
                ServletApiExceptionHandlerSupport.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleUnexpectedException(
                            new IllegalStateException(CANARY_SECRET));

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals(REQUEST_ID, response.getHeaders().getFirst(RequestId.HEADER_NAME));
            assertEquals(REQUEST_ID, response.getBody().getRequestId());
            assertEquals(ApiErrorKey.INTERNAL_ERROR.value(), response.getBody().getErrorKey());
            assertEquals("请求处理失败", response.getBody().getMessage());
            assertFalse(response.toString().contains(CANARY_SECRET));
            assertFalse(appender.list.isEmpty());
            for (ILoggingEvent event : appender.list) {
                assertFalse(event.getFormattedMessage().contains(CANARY_SECRET));
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
