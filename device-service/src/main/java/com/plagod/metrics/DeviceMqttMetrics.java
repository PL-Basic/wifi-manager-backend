package com.plagod.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DeviceMqttMetrics {

    static final String EVENT_METRIC = "wifi.device.mqtt.events";
    static final String CONNECTION_METRIC = "wifi.device.mqtt.connected";
    static final String PUBLISH_METRIC = "wifi.device.mqtt.publish";
    static final String PUBLISH_DURATION_METRIC =
            "wifi.device.mqtt.publish.duration";

    private final MeterRegistry meterRegistry;
    private final AtomicInteger connected = new AtomicInteger();

    public DeviceMqttMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge(CONNECTION_METRIC, connected);
    }

    public void recordAccepted(String topic) {
        record(topic, "accepted");
    }

    public void recordRejected(String topic) {
        record(topic, "rejected");
    }

    public void recordConnectionState(boolean currentConnected) {
        connected.set(currentConnected ? 1 : 0);
    }

    public void recordPublishSuccess(long durationNanos) {
        recordPublish("success", durationNanos);
    }

    public void recordPublishFailure(long durationNanos) {
        recordPublish("failure", durationNanos);
    }

    private void recordPublish(String outcome, long durationNanos) {
        meterRegistry.counter(
                PUBLISH_METRIC,
                "outcome", outcome).increment();
        Timer.builder(PUBLISH_DURATION_METRIC)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(Math.max(0L, durationNanos), TimeUnit.NANOSECONDS);
    }

    private void record(String topic, String outcome) {
        meterRegistry.counter(
                EVENT_METRIC,
                "event", eventType(topic),
                "outcome", outcome).increment();
    }

    private String eventType(String topic) {
        if (topic == null) {
            return "unknown";
        }
        if (topic.endsWith("/event/status")) {
            return "status";
        }
        if (topic.endsWith("/event/traffic")) {
            return "traffic";
        }
        if (topic.endsWith("/event/client-signal")) {
            return "client_signal";
        }
        if (topic.endsWith("/event/client-disconnect")) {
            return "client_disconnect";
        }
        if (topic.endsWith("/event/command-result")) {
            return "command_result";
        }
        return "unknown";
    }
}
