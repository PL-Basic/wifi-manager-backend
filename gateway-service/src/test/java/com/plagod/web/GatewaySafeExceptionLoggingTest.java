package com.plagod.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.request.RequestId;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewaySafeExceptionLoggingTest {

    @Test
    void unhandledExceptionUsesSharedBoundedRenderingWithoutSecrets() {
        RuntimeException exception =
                new RuntimeException(
                        "root-provider-key-canary",
                        new IllegalStateException(
                                "cause-cookie-canary"));
        exception.addSuppressed(
                new IllegalArgumentException(
                        "suppressed-token-canary"));
        StackTraceElement[] frames = new StackTraceElement[20];
        for (int index = 0; index < frames.length; index++) {
            frames[index] = new StackTraceElement(
                    "example.GatewayFrame" + index,
                    "call" + index,
                    "GatewayFrame" + index + ".java",
                    index + 1);
        }
        exception.setStackTrace(frames);

        LogCapture capture = captureUnhandled(exception);

        assertNotNull(capture.event);
        assertNull(capture.event.getThrowableProxy());
        assertTrue(capture.rendered.contains("request-id-00000001"));
        assertTrue(capture.rendered.contains(
                RuntimeException.class.getName()));
        assertTrue(capture.rendered.contains(
                IllegalStateException.class.getName()));
        assertTrue(capture.rendered.contains(
                "example.GatewayFrame11.call11"));
        assertTrue(capture.rendered.contains("8 frames omitted"));
        assertFalse(capture.rendered.contains(
                "example.GatewayFrame12.call12"));
        assertFalse(capture.rendered.contains(
                "root-provider-key-canary"));
        assertFalse(capture.rendered.contains(
                "cause-cookie-canary"));
        assertFalse(capture.rendered.contains(
                "suppressed-token-canary"));
    }

    private LogCapture captureUnhandled(RuntimeException exception) {
        Logger logger = (Logger) LoggerFactory.getLogger(
                GatewayWebExceptionHandler.class);
        Level previousLevel = logger.getLevel();
        boolean previousAdditive = logger.isAdditive();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CapturingOutputAppender appender =
                configuredAppender(logger, output);

        logger.setLevel(Level.ERROR);
        logger.setAdditive(false);
        logger.addAppender(appender);

        try {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/failure").build());
            exchange.getAttributes().put(
                    RequestId.REQUEST_ATTRIBUTE,
                    "request-id-00000001");
            GatewayWebExceptionHandler handler =
                    new GatewayWebExceptionHandler(
                            new GatewayErrorResponseWriter(
                                    new ObjectMapper()));

            handler.handle(exchange, exception).block();

            return new LogCapture(
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

    private static final class LogCapture {

        private final ILoggingEvent event;
        private final String rendered;

        private LogCapture(
                ILoggingEvent event,
                String rendered) {
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
