package com.plagod.verification;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerificationCodeTimeBoundaryTest {

    @Test
    void switchesDailyQuotaAndRedisKeyAtShanghaiMidnight() {
        ZonedDateTime beforeMidnightUtc = ZonedDateTime.ofInstant(
                Instant.parse("2026-08-15T15:59:59.500Z"),
                ZoneOffset.UTC);
        ZonedDateTime midnightUtc = ZonedDateTime.ofInstant(
                Instant.parse("2026-08-15T16:00:00Z"),
                ZoneOffset.UTC);

        assertEquals(
                "20260815",
                VerificationCodeTime.dateKey(beforeMidnightUtc));
        assertEquals(
                LocalDateTime.of(2026, 8, 15, 0, 0),
                VerificationCodeTime.startOfDay(beforeMidnightUtc));
        assertEquals(
                500L,
                VerificationCodeTime.millisUntilNextDay(
                        beforeMidnightUtc));

        assertEquals(
                "20260816",
                VerificationCodeTime.dateKey(midnightUtc));
        assertEquals(
                LocalDateTime.of(2026, 8, 16, 0, 0),
                VerificationCodeTime.startOfDay(midnightUtc));
        assertEquals(
                86_400L,
                VerificationCodeTime.secondsUntilNextDay(midnightUtc));
    }
}
