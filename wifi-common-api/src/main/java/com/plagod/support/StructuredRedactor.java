package com.plagod.support;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 按显式允许列表生成可安全输出的结构化数据。
 */
public final class StructuredRedactor {

    public static final String REDACTED_VALUE = "[REDACTED]";

    private StructuredRedactor() {
    }

    public static Map<String, Object> redact(
            Map<String, ?> input,
            Set<String> allowedKeys,
            Set<String> redactedKeys) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(allowedKeys, "allowedKeys");
        Objects.requireNonNull(redactedKeys, "redactedKeys");

        Map<String, Object> output = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : input.entrySet()) {
            String key = entry.getKey();
            if (!allowedKeys.contains(key)) {
                continue;
            }
            output.put(
                    key,
                    redactedKeys.contains(key)
                            ? REDACTED_VALUE
                            : safeScalarOrRedacted(entry.getValue()));
        }
        return Collections.unmodifiableMap(output);
    }

    private static Object safeScalarOrRedacted(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || isSafeNumber(value)) {
            return value;
        }
        return REDACTED_VALUE;
    }

    private static boolean isSafeNumber(Object value) {
        return value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigInteger
                || value instanceof BigDecimal;
    }
}
