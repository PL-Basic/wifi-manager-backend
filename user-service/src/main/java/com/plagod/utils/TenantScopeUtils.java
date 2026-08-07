package com.plagod.utils;

import org.springframework.util.StringUtils;

public final class TenantScopeUtils {

    private TenantScopeUtils() {
    }

    public static Long requireTenantId(String value) {
        if (!StringUtils.hasText(value) || !value.trim().matches("[1-9]\\d*")) {
            throw new IllegalArgumentException("可信租户身份无效");
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("可信租户身份无效");
        }
    }

    public static Long requireTenantId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("可信租户身份无效");
        }
        return value;
    }

    public static String externalTenantId(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
