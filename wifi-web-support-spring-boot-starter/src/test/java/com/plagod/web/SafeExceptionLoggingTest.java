package com.plagod.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import com.plagod.dto.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeExceptionLoggingTest {

    private static final String ROOT_CANARY = "root-secret-canary";
    private static final String CAUSE_CANARY = "cause-secret-canary";
    private static final String LOCALIZED_CANARY = "localized-secret-canary";
    private static final String SUPPRESSED_CANARY = "suppressed-secret-canary";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void finalLogRenderingKeepsTypesAndFramesWithoutThrowableMessages() {
        LogCapture capture =
                captureUnexpected(exceptionWithCanaries());

        assertEquals(500, capture.response.getStatusCodeValue());
        assertNotNull(capture.event);
        assertNull(capture.event.getThrowableProxy());
        assertTrue(capture.rendered.contains("request-id-00000001"));
        assertTrue(capture.rendered.contains(
                LocalizedCanaryException.class.getName()));
        assertTrue(capture.rendered.contains(
                IllegalStateException.class.getName()));
        assertTrue(capture.rendered.contains("exceptionWithCanaries"));
        assertFalse(capture.rendered.contains(ROOT_CANARY));
        assertFalse(capture.rendered.contains(CAUSE_CANARY));
        assertFalse(capture.rendered.contains(LOCALIZED_CANARY));
        assertFalse(capture.rendered.contains(SUPPRESSED_CANARY));
    }

    @Test
    void maliciousThrowableAccessorsFailClosedInFinalRendering() {
        LogCapture capture =
                captureUnexpected(new AccessorThrowingException());

        assertEquals(500, capture.response.getStatusCodeValue());
        assertEquals(
                "请求处理失败",
                capture.response.getBody().getMessage());
        assertNull(capture.event.getThrowableProxy());
        assertTrue(capture.rendered.contains(
                AccessorThrowingException.class.getName()));
        assertTrue(capture.rendered.contains(
                "[stack trace unavailable]"));
        assertTrue(capture.rendered.contains("[cause unavailable]"));
        assertFalse(capture.rendered.contains(
                "stack-accessor-secret-canary"));
        assertFalse(capture.rendered.contains(
                "cause-accessor-secret-canary"));
        assertFalse(capture.rendered.contains(
                "throwable-message-secret-canary"));
    }

    @Test
    void finalRenderingSanitizesUnicodeFrameControlsButKeepsLocation() {
        RuntimeException exception =
                new RuntimeException(ROOT_CANARY);
        exception.setStackTrace(new StackTraceElement[]{
                new StackTraceElement(
                        "trusted\u0007Injected\u2028Line\u202EFormat"
                                + "\uDB40\uDC01Supplementary",
                        "locate\u2029fake",
                        "Source\u2066\u2067\u2068\u2069.java",
                        73)
        });

        LogCapture capture = captureUnexpected(exception);

        assertTrue(capture.rendered.contains("trusted"));
        assertTrue(capture.rendered.contains("Injected"));
        assertTrue(capture.rendered.contains("Line"));
        assertTrue(capture.rendered.contains("Format"));
        assertTrue(capture.rendered.contains("Supplementary"));
        assertTrue(capture.rendered.contains("locate"));
        assertTrue(capture.rendered.contains("fake"));
        assertTrue(capture.rendered.contains("Source"));
        assertTrue(capture.rendered.contains(".java:73"));
        assertFalse(capture.rendered.contains("\u0007"));
        assertFalse(capture.rendered.contains("\u2028"));
        assertFalse(capture.rendered.contains("\u2029"));
        assertFalse(capture.rendered.contains("\u202E"));
        assertFalse(capture.rendered.contains("\u2066"));
        assertFalse(capture.rendered.contains("\u2067"));
        assertFalse(capture.rendered.contains("\u2068"));
        assertFalse(capture.rendered.contains("\u2069"));
        assertFalse(capture.rendered.contains("\uDB40\uDC01"));
        assertFalse(capture.rendered.contains(ROOT_CANARY));
    }

    @Test
    void formatterBoundsFramesAndCauseDepthAndHandlesCycles() {
        RuntimeException frameHeavy =
                new RuntimeException(ROOT_CANARY);
        StackTraceElement[] frames = new StackTraceElement[20];
        for (int index = 0; index < frames.length; index++) {
            frames[index] = new StackTraceElement(
                    "example.Frame" + index,
                    "call" + index,
                    "Frame" + index + ".java",
                    index + 1);
        }
        frameHeavy.setStackTrace(frames);

        String boundedFrames =
                SafeExceptionLogFormatter.format(frameHeavy);
        assertTrue(boundedFrames.contains("example.Frame11.call11"));
        assertFalse(boundedFrames.contains("example.Frame12.call12"));
        assertTrue(boundedFrames.contains("8 frames omitted"));
        assertFalse(boundedFrames.contains(ROOT_CANARY));

        RuntimeException deep = new RuntimeException("depth-9-canary");
        for (int depth = 8; depth >= 0; depth--) {
            deep = new RuntimeException("depth-" + depth + "-canary", deep);
        }
        String boundedCauses = SafeExceptionLogFormatter.format(deep);
        assertTrue(boundedCauses.contains("cause depth limit reached"));
        assertFalse(boundedCauses.contains("depth-0-canary"));
        assertFalse(boundedCauses.contains("depth-9-canary"));

        RuntimeException first = new RuntimeException("cycle-first-canary");
        RuntimeException second = new RuntimeException("cycle-second-canary");
        first.initCause(second);
        second.initCause(first);
        String circular = SafeExceptionLogFormatter.format(first);
        assertTrue(circular.contains("circular cause"));
        assertFalse(circular.contains("cycle-first-canary"));
        assertFalse(circular.contains("cycle-second-canary"));
    }

    private LogCapture captureUnexpected(RuntimeException exception) {
        Logger logger = (Logger) LoggerFactory.getLogger(
                ServletApiExceptionHandlerSupport.class);
        Level previousLevel = logger.getLevel();
        boolean previousAdditive = logger.isAdditive();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CapturingOutputAppender appender =
                configuredAppender(logger, output);

        logger.setLevel(Level.ERROR);
        logger.setAdditive(false);
        logger.addAppender(appender);
        MDC.put(RequestIdContext.MDC_KEY, "request-id-00000001");

        try {
            ServletApiExceptionHandlerSupport handler =
                    new ServletApiExceptionHandlerSupport(
                            new ApiErrorResponseFactory());
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleUnexpectedException(exception);
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
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
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

    private RuntimeException exceptionWithCanaries() {
        IllegalStateException cause =
                new IllegalStateException(CAUSE_CANARY);
        LocalizedCanaryException exception =
                new LocalizedCanaryException(ROOT_CANARY, cause);
        exception.addSuppressed(
                new IllegalArgumentException(SUPPRESSED_CANARY));
        return exception;
    }

    private static final class AccessorThrowingException
            extends RuntimeException {

        private AccessorThrowingException() {
            super("throwable-message-secret-canary");
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            throw new AssertionError(
                    "stack-accessor-secret-canary");
        }

        @Override
        public synchronized Throwable getCause() {
            throw new AssertionError(
                    "cause-accessor-secret-canary");
        }
    }

    private static final class LocalizedCanaryException
            extends RuntimeException {

        private LocalizedCanaryException(
                String message,
                Throwable cause) {
            super(message, cause);
        }

        @Override
        public String getLocalizedMessage() {
            return LOCALIZED_CANARY;
        }
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
