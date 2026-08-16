package com.plagod.testkit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundedAwaitTest {

    @Test
    void returnsWhenProbeBecomesSatisfiedWithinBound() throws Exception {
        AtomicInteger probes = new AtomicInteger();
        AtomicLong time = new AtomicLong();

        Integer result = BoundedAwait.until(
                "ready",
                Duration.ofSeconds(1),
                Duration.ofMillis(100),
                probes::incrementAndGet,
                value -> value == 3,
                time::get,
                millis -> time.addAndGet(Duration.ofMillis(millis).toNanos()));

        assertEquals(3, result);
    }

    @Test
    void throwsOneBoundedTimeoutWithoutRetryingTheWrite() {
        AtomicLong time = new AtomicLong();

        assertThrows(TimeoutException.class, () -> BoundedAwait.until(
                "never-ready",
                Duration.ofMillis(200),
                Duration.ofMillis(100),
                () -> false,
                value -> value,
                time::get,
                millis -> time.addAndGet(Duration.ofMillis(millis).toNanos())));
    }
}
