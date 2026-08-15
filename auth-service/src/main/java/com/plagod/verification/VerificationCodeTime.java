package com.plagod.verification;

import com.plagod.support.StableUnits;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统一验证码持久化时间、日额度和 Redis 日键的业务时区。
 */
public final class VerificationCodeTime {

    private VerificationCodeTime() {
    }

    public static ZonedDateTime now() {
        return ZonedDateTime.now(StableUnits.ASIA_SHANGHAI);
    }

    public static LocalDateTime currentLocalDateTime() {
        return now().toLocalDateTime();
    }

    public static LocalDateTime localDateTime(ZonedDateTime time) {
        return normalize(time).toLocalDateTime();
    }

    public static LocalDateTime startOfDay(ZonedDateTime time) {
        return normalize(time)
                .toLocalDate()
                .atStartOfDay();
    }

    public static String dateKey(ZonedDateTime time) {
        return normalize(time)
                .toLocalDate()
                .format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    public static long millisUntilNextDay(ZonedDateTime time) {
        ZonedDateTime businessTime = normalize(time);
        return Math.max(
                1L,
                Duration.between(
                        businessTime,
                        nextDayStart(businessTime)).toMillis());
    }

    public static long secondsUntilNextDay(ZonedDateTime time) {
        ZonedDateTime businessTime = normalize(time);
        return Math.max(
                1L,
                Duration.between(
                        businessTime,
                        nextDayStart(businessTime)).getSeconds());
    }

    private static ZonedDateTime nextDayStart(
            ZonedDateTime businessTime) {
        return businessTime
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(StableUnits.ASIA_SHANGHAI);
    }

    private static ZonedDateTime normalize(ZonedDateTime time) {
        if (time == null) {
            throw new IllegalArgumentException(
                    "verification time must not be null");
        }
        return time.withZoneSameInstant(StableUnits.ASIA_SHANGHAI);
    }
}
