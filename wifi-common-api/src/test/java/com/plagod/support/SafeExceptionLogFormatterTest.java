package com.plagod.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeExceptionLogFormatterTest {

    @Test
    void keepsTypesAndLocationsWithoutMessagesOrSuppressedValues() {
        IllegalStateException cause =
                new IllegalStateException("cause-secret-canary");
        RuntimeException exception =
                new RuntimeException("root-secret-canary", cause);
        exception.addSuppressed(
                new IllegalArgumentException("suppressed-secret-canary"));
        exception.setStackTrace(new StackTraceElement[]{
                new StackTraceElement(
                        "example.SafeFrame",
                        "invoke",
                        "SafeFrame.java",
                        73)
        });

        String rendered = SafeExceptionLogFormatter.format(exception);

        assertTrue(rendered.contains(RuntimeException.class.getName()));
        assertTrue(rendered.contains(IllegalStateException.class.getName()));
        assertTrue(rendered.contains(
                "example.SafeFrame.invoke(SafeFrame.java:73)"));
        assertFalse(rendered.contains("root-secret-canary"));
        assertFalse(rendered.contains("cause-secret-canary"));
        assertFalse(rendered.contains("suppressed-secret-canary"));
    }

    @Test
    void boundsFramesAndCauseDepthAndStopsCycles() {
        RuntimeException frameHeavy =
                new RuntimeException("frame-secret-canary");
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

    @Test
    void throwableAccessorFailuresRemainSafe() {
        String rendered = SafeExceptionLogFormatter.format(
                new AccessorThrowingException());

        assertTrue(rendered.contains("[stack trace unavailable]"));
        assertTrue(rendered.contains("[cause unavailable]"));
        assertFalse(rendered.contains("stack-accessor-secret-canary"));
        assertFalse(rendered.contains("cause-accessor-secret-canary"));
        assertFalse(rendered.contains("throwable-message-secret-canary"));
    }

    @Test
    void frameControlsAreReplacedWithoutLosingLocation() {
        RuntimeException exception =
                new RuntimeException("frame-control-secret-canary");
        exception.setStackTrace(new StackTraceElement[]{
                new StackTraceElement(
                        "trusted\u0007Injected\u2028Line\u202EFormat"
                                + "\uDB40\uDC01Supplementary",
                        "locate\u2029fake",
                        "Source\u2066\u2067\u2068\u2069.java",
                        91)
        });

        String rendered = SafeExceptionLogFormatter.format(exception);

        assertTrue(rendered.contains("trusted"));
        assertTrue(rendered.contains("Supplementary"));
        assertTrue(rendered.contains(".java:91"));
        assertFalse(rendered.contains("\u0007"));
        assertFalse(rendered.contains("\u2028"));
        assertFalse(rendered.contains("\u2029"));
        assertFalse(rendered.contains("\u202E"));
        assertFalse(rendered.contains("\u2066"));
        assertFalse(rendered.contains("\u2067"));
        assertFalse(rendered.contains("\u2068"));
        assertFalse(rendered.contains("\u2069"));
        assertFalse(rendered.contains("\uDB40\uDC01"));
        assertFalse(rendered.contains("frame-control-secret-canary"));
    }

    private static final class AccessorThrowingException
            extends RuntimeException {

        private AccessorThrowingException() {
            super("throwable-message-secret-canary");
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            throw new AssertionError("stack-accessor-secret-canary");
        }

        @Override
        public synchronized Throwable getCause() {
            throw new AssertionError("cause-accessor-secret-canary");
        }
    }
}
