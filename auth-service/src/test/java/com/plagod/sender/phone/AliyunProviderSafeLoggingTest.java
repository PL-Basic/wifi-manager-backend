package com.plagod.sender.phone;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.plagod.configuration.PhoneVerificationProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliyunProviderSafeLoggingTest {

    @Test
    void providerLogKeepsTypeAndStackWithoutExceptionMessage() {
        String canary = "provider-secret-canary";
        RuntimeException failure = new RuntimeException(canary);
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(
                        "com.plagod.ProviderClient",
                        "send",
                        "ProviderClient.java",
                        42)
        });

        Logger logger = (Logger) LoggerFactory.getLogger(
                AliyunNumberAuthVerificationProvider.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new AliyunNumberAuthVerificationProvider(
                    new PhoneVerificationProperties(
                            new MockEnvironment()),
                    new SimpleMeterRegistry())
                    .logProviderFailure("send", failure);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String rendered = appender.list.get(0).getFormattedMessage();
        assertFalse(rendered.contains(canary));
        assertTrue(rendered.contains(RuntimeException.class.getName()));
        assertTrue(rendered.contains("ProviderClient.java:42"));
        assertNull(appender.list.get(0).getThrowableProxy());
    }
}
