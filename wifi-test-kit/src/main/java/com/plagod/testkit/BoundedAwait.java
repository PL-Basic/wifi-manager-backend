package com.plagod.testkit;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class BoundedAwait {

    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration MAX_INTERVAL = Duration.ofSeconds(5);

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private BoundedAwait() {
    }

    public static <T> T until(
            String name,
            Duration timeout,
            Duration interval,
            Supplier<T> probe,
            Predicate<T> satisfied) throws TimeoutException {

        return until(
                name,
                timeout,
                interval,
                probe,
                satisfied,
                System::nanoTime,
                Thread::sleep);
    }

    public static <T> T until(
            String name,
            Duration timeout,
            Duration interval,
            Supplier<T> probe,
            Predicate<T> satisfied,
            LongSupplier nanoTime,
            Sleeper sleeper) throws TimeoutException {

        requireDuration(timeout, MAX_TIMEOUT, "timeout");
        requireDuration(interval, MAX_INTERVAL, "interval");
        Objects.requireNonNull(probe, "probe");
        Objects.requireNonNull(satisfied, "satisfied");
        Objects.requireNonNull(nanoTime, "nanoTime");
        Objects.requireNonNull(sleeper, "sleeper");

        long deadline = nanoTime.getAsLong() + timeout.toNanos();
        while (true) {
            T result = probe.get();
            if (satisfied.test(result)) {
                return result;
            }
            if (nanoTime.getAsLong() >= deadline) {
                throw new TimeoutException("timed out waiting for " + safeName(name));
            }
            try {
                sleeper.sleep(interval.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for " + safeName(name));
            }
        }
    }

    private static void requireDuration(Duration value, Duration maximum, String name) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(name + " must be positive and at most " + maximum);
        }
    }

    private static String safeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "condition";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
