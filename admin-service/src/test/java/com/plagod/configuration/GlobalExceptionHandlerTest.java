package com.plagod.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import com.plagod.client.AdminDownstreamException;
import com.plagod.dto.ApiResponse;
import com.plagod.exception.ApiErrorKey;
import com.plagod.request.RequestId;
import com.plagod.web.ApiErrorResponseFactory;
import com.plagod.web.RequestIdContext;
import com.plagod.web.ServletApiExceptionHandlerSupport;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private static final String REQUEST_ID =
            "request-id-00000001";
    private static final String INTERNAL_CANARY =
            "CANARY_INTERNAL_SECRET";
    private static final String DOWNSTREAM_BODY_CANARY =
            "CANARY_DOWNSTREAM_SECRET_BODY";

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(
                    new ApiErrorResponseFactory());

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldPreserveSafeDownstreamStatusesAndErrorKeys() {
        assertStatus(
                HttpStatus.BAD_REQUEST,
                ApiErrorKey.VALIDATION_FAILED);
        assertStatus(
                HttpStatus.NOT_FOUND,
                ApiErrorKey.RESOURCE_NOT_FOUND);
        assertStatus(
                HttpStatus.CONFLICT,
                ApiErrorKey.RESOURCE_VERSION_CONFLICT);
    }

    @Test
    void shouldPreserveRateLimitWithSafeRetryAfter() {
        MDC.put(RequestIdContext.MDC_KEY, REQUEST_ID);
        AdminDownstreamException exception =
                new AdminDownstreamException(
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        17L);

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAdminDownstreamException(exception);

        assertResponse(
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                ApiErrorKey.RATE_LIMITED);
        assertEquals(
                "17",
                response.getHeaders().getFirst(
                        HttpHeaders.RETRY_AFTER));
        assertEquals(
                REQUEST_ID,
                response.getHeaders().getFirst(
                        RequestId.HEADER_NAME));
        assertEquals(
                REQUEST_ID,
                response.getBody().getRequestId());
    }

    @Test
    void shouldDefaultMissingRateLimitDelayToOneSecond() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAdminDownstreamException(
                        new AdminDownstreamException(
                                HttpStatus.TOO_MANY_REQUESTS.value(),
                                null));

        assertResponse(
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                ApiErrorKey.RATE_LIMITED);
        assertEquals(
                "1",
                response.getHeaders().getFirst(
                        HttpHeaders.RETRY_AFTER));
    }

    @Test
    void shouldHideDownstreamAuthenticationAndAuthorization() {
        assertHiddenStatus(HttpStatus.UNAUTHORIZED);
        assertHiddenStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldMapTransportAndUnknownFailuresToUnavailable() {
        FeignException exception = mock(FeignException.class);
        when(exception.status()).thenReturn(-1);

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleFeignException(exception);

        assertResponse(
                response,
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorKey.DEPENDENCY_UNAVAILABLE);
        assertNull(response.getHeaders().getFirst(
                HttpHeaders.RETRY_AFTER));
    }

    @Test
    void finalFeignLogRenderingNeverExposesDownstreamBody() {
        MDC.put(RequestIdContext.MDC_KEY, REQUEST_ID);
        FeignException exception = feignExceptionWithBody(
                HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertTrue(exception.contentUTF8().contains(
                DOWNSTREAM_BODY_CANARY));

        LogCapture capture = capture(
                (Logger) LoggerFactory.getLogger(
                        GlobalExceptionHandler.class),
                Level.WARN,
                () -> handler.handleFeignException(exception));

        ResponseEntity<ApiResponse<Void>> response =
                capture.response;
        assertResponse(
                response,
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorKey.DEPENDENCY_UNAVAILABLE);
        assertNotNull(capture.event);
        assertNull(capture.event.getThrowableProxy());
        assertTrue(capture.rendered.contains(REQUEST_ID));
        assertTrue(capture.rendered.contains("status=500"));
        assertFalse(capture.rendered.contains(
                DOWNSTREAM_BODY_CANARY));
        assertFalse(response.getBody().getMessage().contains(
                DOWNSTREAM_BODY_CANARY));
    }

    @Test
    void sharedUnexpectedFinalRenderingOmitsExceptionMessage() {
        MDC.put(RequestIdContext.MDC_KEY, REQUEST_ID);
        LogCapture capture = capture(
                (Logger) LoggerFactory.getLogger(
                        ServletApiExceptionHandlerSupport.class),
                Level.ERROR,
                () -> handler.handleUnexpectedException(
                        new IllegalStateException(
                                INTERNAL_CANARY)));

        ResponseEntity<ApiResponse<Void>> response =
                capture.response;
        assertResponse(
                response,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorKey.INTERNAL_ERROR);
        assertEquals("请求处理失败",
                response.getBody().getMessage());
        assertNotNull(capture.event);
        assertNull(capture.event.getThrowableProxy());
        assertTrue(capture.rendered.contains(REQUEST_ID));
        assertTrue(capture.rendered.contains(
                IllegalStateException.class.getName()));
        assertTrue(capture.rendered.contains(
                "sharedUnexpectedFinalRenderingOmitsExceptionMessage"));
        assertFalse(capture.rendered.contains(INTERNAL_CANARY));
        assertFalse(response.getBody().getMessage().contains(
                INTERNAL_CANARY));
    }

    private void assertStatus(
            HttpStatus status,
            ApiErrorKey errorKey) {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAdminDownstreamException(
                        new AdminDownstreamException(
                                status.value(),
                                null));
        assertResponse(response, status, errorKey);
    }

    private void assertHiddenStatus(HttpStatus downstreamStatus) {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAdminDownstreamException(
                        new AdminDownstreamException(
                                downstreamStatus.value(),
                                null));
        assertResponse(
                response,
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorKey.DEPENDENCY_UNAVAILABLE);
    }

    private void assertResponse(
            ResponseEntity<ApiResponse<Void>> response,
            HttpStatus status,
            ApiErrorKey errorKey) {
        assertEquals(status, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(status.value(),
                response.getBody().getCode());
        assertEquals(errorKey.value(),
                response.getBody().getErrorKey());
        assertNotNull(response.getBody().getRequestId());
        assertEquals(
                response.getBody().getRequestId(),
                response.getHeaders().getFirst(
                        RequestId.HEADER_NAME));
    }

    private FeignException feignExceptionWithBody(int status) {
        Request request = Request.create(
                "GET",
                "http://downstream.invalid/test",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8);
        Response response = Response.builder()
                .status(status)
                .reason("downstream error")
                .headers(Collections.emptyMap())
                .request(request)
                .body(DOWNSTREAM_BODY_CANARY,
                        StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus(
                "UserServiceClient#find",
                response);
    }

    private LogCapture capture(
            Logger logger,
            Level level,
            Supplier<ResponseEntity<ApiResponse<Void>>> invocation) {
        Level previousLevel = logger.getLevel();
        boolean previousAdditive = logger.isAdditive();
        ByteArrayOutputStream output =
                new ByteArrayOutputStream();
        CapturingOutputAppender appender =
                configuredAppender(logger, output);

        logger.setLevel(level);
        logger.setAdditive(false);
        logger.addAppender(appender);
        try {
            ResponseEntity<ApiResponse<Void>> response =
                    invocation.get();
            return new LogCapture(
                    response,
                    appender.event,
                    new String(
                            output.toByteArray(),
                            StandardCharsets.UTF_8));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setAdditive(previousAdditive);
            logger.setLevel(previousLevel);
        }
    }

    private CapturingOutputAppender configuredAppender(
            Logger logger,
            ByteArrayOutputStream output) {
        PatternLayoutEncoder encoder =
                new PatternLayoutEncoder();
        encoder.setContext(logger.getLoggerContext());
        encoder.setPattern("%msg%n%ex");
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();

        CapturingOutputAppender appender =
                new CapturingOutputAppender();
        appender.setContext(logger.getLoggerContext());
        appender.setEncoder(encoder);
        appender.setOutputStream(output);
        appender.start();
        return appender;
    }

    private static final class LogCapture {

        private final ResponseEntity<ApiResponse<Void>> response;
        private final ILoggingEvent event;
        private final String rendered;

        private LogCapture(
                ResponseEntity<ApiResponse<Void>> response,
                ILoggingEvent event,
                String rendered) {
            this.response = response;
            this.event = event;
            this.rendered = rendered;
        }
    }

    private static final class CapturingOutputAppender
            extends OutputStreamAppender<ILoggingEvent> {

        private ILoggingEvent event;

        @Override
        protected void append(ILoggingEvent eventObject) {
            event = eventObject;
            super.append(eventObject);
        }
    }
}
