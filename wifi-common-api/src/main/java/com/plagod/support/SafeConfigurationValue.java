package com.plagod.support;

import java.util.Collection;
import java.util.Objects;

/**
 * 校验配置值，并确保失败信息不回显配置内容。
 */
public final class SafeConfigurationValue {

    private SafeConfigurationValue() {
    }

    public static String requireText(String propertyName, String value) {
        requirePropertyName(propertyName);
        if (value == null || value.trim().isEmpty()) {
            throw violation(
                    propertyName,
                    "must contain non-whitespace text");
        }
        return value;
    }

    public static String requireSecret(
            String propertyName,
            String value,
            int minimumLength,
            Collection<String> forbiddenExactValues) {
        requirePropertyName(propertyName);
        if (minimumLength < 1) {
            throw new IllegalArgumentException(
                    "minimumLength must be at least 1");
        }
        Objects.requireNonNull(
                forbiddenExactValues,
                "forbiddenExactValues");

        requireText(propertyName, value);
        if (value.length() < minimumLength) {
            throw violation(
                    propertyName,
                    "must contain at least "
                            + minimumLength
                            + " characters");
        }
        if (forbiddenExactValues.contains(value)) {
            throw violation(
                    propertyName,
                    "must not use a forbidden exact value");
        }
        return value;
    }

    private static void requirePropertyName(String propertyName) {
        if (propertyName == null || propertyName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "propertyName must contain non-whitespace text");
        }
    }

    private static IllegalStateException violation(
            String propertyName,
            String rule) {
        return new IllegalStateException(propertyName + " " + rule);
    }
}
