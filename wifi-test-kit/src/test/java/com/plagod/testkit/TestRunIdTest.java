package com.plagod.testkit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRunIdTest {

    @Test
    void createsDeterministicBoundedAsciiRunId() {
        String runId = TestRunId.create(
                "Stage 01",
                "Tenant Contract",
                Clock.fixed(Instant.parse("2026-08-10T12:34:56Z"), ZoneOffset.UTC),
                () -> "abc123");

        assertEquals("wm-stage-01-tenant-contract-123456-abc123", runId);
        assertTrue(runId.length() <= 48);
    }
}
