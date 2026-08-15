package com.plagod.support;

import java.time.ZoneId;

/**
 * 不依赖业务语义的稳定单位常量。
 */
public final class StableUnits {

    public static final int CENTS_PER_MAJOR_UNIT = 100;
    public static final int SECONDS_PER_MINUTE = 60;
    public static final int MILLISECONDS_PER_SECOND = 1_000;
    public static final int BYTES_PER_KIBIBYTE = 1_024;
    public static final String ASIA_SHANGHAI_ZONE_ID = "Asia/Shanghai";
    public static final ZoneId ASIA_SHANGHAI =
            ZoneId.of(ASIA_SHANGHAI_ZONE_ID);

    private StableUnits() {
    }
}
