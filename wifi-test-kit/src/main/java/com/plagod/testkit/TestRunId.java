package com.plagod.testkit;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

public final class TestRunId {

    private static final int MAX_LENGTH = 48;
    private static final int MAX_STAGE_LENGTH = 12;
    private static final int MAX_CASE_LENGTH = 16;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");
    private static final char[] RANDOM_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private TestRunId() {
    }

    public static String create(String stage, String caseName) {
        return create(stage, caseName, Clock.systemUTC(), TestRunId::randomSuffix);
    }

    public static String create(
            String stage,
            String caseName,
            Clock clock,
            Supplier<String> randomSupplier) {

        String normalizedStage = boundedComponent(stage, "stage", MAX_STAGE_LENGTH);
        String normalizedCase = boundedComponent(caseName, "caseName", MAX_CASE_LENGTH);
        String time = LocalTime.now(Objects.requireNonNull(clock, "clock")).format(TIME_FORMAT);
        String random = boundedComponent(
                Objects.requireNonNull(randomSupplier, "randomSupplier").get(),
                "random",
                6);
        if (random.length() != 6) {
            throw new IllegalArgumentException("random must contain exactly 6 safe characters");
        }

        String runId = String.format(
                Locale.ROOT,
                "wm-%s-%s-%s-%s",
                normalizedStage,
                normalizedCase,
                time,
                random);
        if (runId.length() > MAX_LENGTH) {
            throw new IllegalStateException("generated Run ID exceeds 48 characters");
        }
        return runId;
    }

    private static String boundedComponent(String value, String name, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }

        String normalized = value
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must contain ASCII letters or digits");
        }
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    private static String randomSuffix() {
        char[] suffix = new char[6];
        for (int index = 0; index < suffix.length; index++) {
            suffix[index] = RANDOM_ALPHABET[RANDOM.nextInt(RANDOM_ALPHABET.length)];
        }
        return new String(suffix);
    }
}
