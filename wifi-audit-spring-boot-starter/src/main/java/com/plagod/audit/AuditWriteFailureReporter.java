package com.plagod.audit;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 只报告固定低基数失败信号，不输出异常正文。
 */
class AuditWriteFailureReporter {

    private static final Logger log = LoggerFactory.getLogger(
            AuditWriteFailureReporter.class);

    private final MeterRegistry meterRegistry;
    private final AtomicLong writeFailures = new AtomicLong();
    private final AtomicLong droppedEvents = new AtomicLong();

    AuditWriteFailureReporter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void writerFailure(String mode) {
        writeFailures.incrementAndGet();
        increment(
                "wifi.audit.write.failures",
                "mode",
                mode);
        log.warn("audit write failed; mode={}", mode);
    }

    void invalidMetadata() {
        dropEvent("invalid_metadata");
    }

    void unsupportedAsyncResult() {
        dropEvent("unsupported_async_result");
    }

    private void dropEvent(String reason) {
        droppedEvents.incrementAndGet();
        increment(
                "wifi.audit.events.dropped",
                "reason",
                reason);
        log.warn("audit event dropped; reason={}", reason);
    }

    private void increment(
            String name,
            String tagName,
            String tagValue) {
        if (meterRegistry == null) {
            return;
        }
        try {
            meterRegistry.counter(
                    name,
                    tagName,
                    tagValue).increment();
        } catch (RuntimeException exception) {
            log.warn("audit metric update failed; meter={}", name);
        }
    }

    long getWriteFailures() {
        return writeFailures.get();
    }

    long getDroppedEvents() {
        return droppedEvents.get();
    }
}
