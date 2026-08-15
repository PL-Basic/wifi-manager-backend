package com.plagod.ai.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class AiProviderMetrics {

    public static final String REVIEW_DURATION_METRIC =
            "wifi.ai.provider.review.duration";

    private final MeterRegistry meterRegistry;

    public AiProviderMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(
                meterRegistry,
                "meterRegistry");
    }

    public void record(
            AiModerationMetricOutcome outcome,
            long durationNanos) {
        Objects.requireNonNull(outcome, "outcome");
        Timer.builder(REVIEW_DURATION_METRIC)
                .tags(
                        "event", "review",
                        "result", outcome.getResult(),
                        "type", outcome.getType())
                .register(meterRegistry)
                .record(
                        Math.max(0L, durationNanos),
                        TimeUnit.NANOSECONDS);
    }
}
