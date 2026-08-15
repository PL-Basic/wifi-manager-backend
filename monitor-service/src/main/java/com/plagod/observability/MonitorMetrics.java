package com.plagod.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Monitor 领域指标只使用固定结果标签，不携带身份或请求标识。
 */
@Component
public class MonitorMetrics {

    public static final String TRAFFIC_EVALUATIONS =
            "wifi.monitor.traffic.evaluations";
    public static final String SUPPRESSED_RULE_HITS =
            "wifi.monitor.rule.hits.suppressed";

    private final Counter alertCreated;
    private final Counter noAlert;
    private final Counter suppressedRuleHits;

    public MonitorMetrics(MeterRegistry meterRegistry) {
        this.alertCreated = Counter.builder(TRAFFIC_EVALUATIONS)
                .tag("result", "ALERT_CREATED")
                .register(meterRegistry);
        this.noAlert = Counter.builder(TRAFFIC_EVALUATIONS)
                .tag("result", "NO_ALERT")
                .register(meterRegistry);
        this.suppressedRuleHits =
                Counter.builder(SUPPRESSED_RULE_HITS)
                        .register(meterRegistry);
    }

    public void recordTrafficEvaluation(
            boolean createdAlert,
            int suppressedHits) {
        (createdAlert ? alertCreated : noAlert).increment();
        if (suppressedHits > 0) {
            suppressedRuleHits.increment(suppressedHits);
        }
    }
}
