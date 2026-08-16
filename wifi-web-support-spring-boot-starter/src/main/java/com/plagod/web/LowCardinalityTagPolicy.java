package com.plagod.web;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 约束共享基础设施指标只能使用低基数、非身份型标签。
 */
public final class LowCardinalityTagPolicy implements MeterFilter {

    public static final int MAX_VALUE_LENGTH = 128;

    private static final String WIFI_METER_PREFIX = "wifi.";
    private static final Pattern STATUS_VALUE =
            Pattern.compile("[1-5][0-9]{2}");
    private static final Pattern TOKEN_VALUE =
            Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,31}");
    private static final Pattern UUID_PATH_SEGMENT = Pattern.compile(
            "(?i)(?:^|/)[0-9a-f]{8}(?:-[0-9a-f]{4}){3}"
                    + "-[0-9a-f]{12}(?:/|$)");
    private static final Pattern URI_TEMPLATE_VALUE = Pattern.compile(
            "/(?:(?:[A-Za-z][A-Za-z0-9._-]*"
                    + "|\\{[A-Za-z][A-Za-z0-9]*\\})"
                    + "(?:/(?:[A-Za-z][A-Za-z0-9._-]*"
                    + "|\\{[A-Za-z][A-Za-z0-9]*\\}))*"
                    + ")?");
    private static final Map<String, TagSchema> TAG_SCHEMAS =
            createTagSchemas();

    private final Map<String, Set<String>> observedValues;
    private final int maximumDistinctValuesPerKey;

    public LowCardinalityTagPolicy() {
        this(Integer.MAX_VALUE);
    }

    LowCardinalityTagPolicy(int maximumDistinctValuesPerKey) {
        if (maximumDistinctValuesPerKey < 1) {
            throw new IllegalArgumentException(
                    "maximum distinct values must be positive");
        }
        this.maximumDistinctValuesPerKey =
                maximumDistinctValuesPerKey;
        this.observedValues = new LinkedHashMap<>();
        for (String key : TAG_SCHEMAS.keySet()) {
            observedValues.put(key, new HashSet<>());
        }
    }

    @Override
    public MeterFilterReply accept(Meter.Id id) {
        if (id == null || id.getName() == null
                || !id.getName().startsWith(WIFI_METER_PREFIX)) {
            return MeterFilterReply.NEUTRAL;
        }

        List<Tag> tags = id.getTags();
        for (Tag tag : tags) {
            if (!isAllowed(tag)) {
                return MeterFilterReply.DENY;
            }
        }

        synchronized (observedValues) {
            for (Tag tag : tags) {
                TagSchema schema = TAG_SCHEMAS.get(tag.getKey());
                Set<String> values = observedValues.get(tag.getKey());
                int limit = Math.min(
                        schema.maximumDistinctValues,
                        maximumDistinctValuesPerKey);
                if (!values.contains(tag.getValue())
                        && values.size() >= limit) {
                    return MeterFilterReply.DENY;
                }
            }
            for (Tag tag : tags) {
                observedValues.get(tag.getKey()).add(tag.getValue());
            }
        }
        return MeterFilterReply.NEUTRAL;
    }

    public boolean isAllowed(Tag tag) {
        return tag != null && isAllowed(tag.getKey(), tag.getValue());
    }

    public boolean isAllowed(String key, String value) {
        TagSchema schema = TAG_SCHEMAS.get(key);
        return schema != null
                && schema.matches(value)
                && (!"uri".equals(key)
                || !UUID_PATH_SEGMENT.matcher(value).find());
    }

    public void validate(Tag tag) {
        if (!isAllowed(tag)) {
            throw new IllegalArgumentException(
                    "metric tag violates the low-cardinality policy");
        }
    }

    int observedValueCount(String key) {
        synchronized (observedValues) {
            Set<String> values = observedValues.get(key);
            return values == null ? 0 : values.size();
        }
    }

    private static Map<String, TagSchema> createTagSchemas() {
        Map<String, TagSchema> schemas = new LinkedHashMap<>();
        schemas.put(
                "method",
                TagSchema.enumerated(
                        8,
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS",
                        "HEAD"));
        schemas.put("status", TagSchema.pattern(32, STATUS_VALUE));
        schemas.put(
                "outcome",
                TagSchema.enumerated(
                        6,
                        "SUCCESS",
                        "REDIRECTION",
                        "CLIENT_ERROR",
                        "SERVER_ERROR",
                        "UNKNOWN"));
        schemas.put("event", TagSchema.pattern(32, TOKEN_VALUE));
        schemas.put("result", TagSchema.pattern(32, TOKEN_VALUE));
        schemas.put("type", TagSchema.pattern(32, TOKEN_VALUE));
        schemas.put(
                "uri",
                TagSchema.pattern(64, URI_TEMPLATE_VALUE));
        return Collections.unmodifiableMap(schemas);
    }

    private static final class TagSchema {

        private final int maximumDistinctValues;
        private final Set<String> values;
        private final Pattern pattern;

        private TagSchema(
                int maximumDistinctValues,
                Set<String> values,
                Pattern pattern) {
            this.maximumDistinctValues = maximumDistinctValues;
            this.values = values;
            this.pattern = pattern;
        }

        private static TagSchema enumerated(
                int maximumDistinctValues,
                String... allowedValues) {
            Set<String> values = new HashSet<>();
            Collections.addAll(values, allowedValues);
            return new TagSchema(
                    maximumDistinctValues,
                    Collections.unmodifiableSet(values),
                    null);
        }

        private static TagSchema pattern(
                int maximumDistinctValues,
                Pattern pattern) {
            return new TagSchema(
                    maximumDistinctValues,
                    Collections.<String>emptySet(),
                    pattern);
        }

        private boolean matches(String value) {
            if (value == null || value.isEmpty()
                    || value.length() > MAX_VALUE_LENGTH) {
                return false;
            }
            return pattern == null
                    ? values.contains(value)
                    : pattern.matcher(value).matches();
        }
    }
}
