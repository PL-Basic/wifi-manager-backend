package com.plagod.observability;

import com.plagod.web.LowCardinalityTagPolicy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonitorMetricsTest {

    @Test
    void recordsOnlyFixedLowCardinalityDomainResults() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LowCardinalityTagPolicy policy = new LowCardinalityTagPolicy();
        registry.config().meterFilter(policy);

        MonitorMetrics metrics = new MonitorMetrics(registry);
        metrics.recordTrafficEvaluation(false, 2);
        metrics.recordTrafficEvaluation(true, 0);

        assertEquals(
                1.0D,
                counter(registry, "NO_ALERT").count());
        assertEquals(
                1.0D,
                counter(registry, "ALERT_CREATED").count());
        assertEquals(
                2.0D,
                registry.get(MonitorMetrics.SUPPRESSED_RULE_HITS)
                        .counter()
                        .count());

        for (Meter meter : registry.getMeters()) {
            assertEquals(
                    MeterFilterReply.NEUTRAL,
                    policy.accept(meter.getId()));
        }
    }

    private Counter counter(
            SimpleMeterRegistry registry,
            String result) {
        return registry.get(MonitorMetrics.TRAFFIC_EVALUATIONS)
                .tag("result", result)
                .counter();
    }
}
