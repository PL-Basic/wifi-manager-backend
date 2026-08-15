package com.plagod.ai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class AiModerationResult {

    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

    private final AiModerationDecision decision;
    private final double confidence;
    private final String reasonCode;
    private final List<String> riskLabels;
    private final String category;
    private final AiModerationPriority priority;
    private final String providerRequestId;
    private final String model;
    private final Long latencyMillis;

    public AiModerationResult(
            AiModerationDecision decision,
            double confidence,
            String reasonCode,
            List<String> riskLabels,
            String category,
            AiModerationPriority priority,
            String providerRequestId,
            String model,
            Long latencyMillis) {
        this.decision = Objects.requireNonNull(decision, "decision");
        if (Double.isNaN(confidence)
                || Double.isInfinite(confidence)
                || confidence < 0.0d
                || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence 必须在 0..1");
        }
        this.confidence = confidence;
        this.reasonCode = requireCode(reasonCode, "reasonCode");
        this.riskLabels = immutableLabels(riskLabels);
        this.category = boundedOptional(category, 64, "category");
        this.priority = priority;
        this.providerRequestId =
                boundedOptional(providerRequestId, 128, "providerRequestId");
        this.model = boundedOptional(model, 96, "model");
        if (latencyMillis != null
                && (latencyMillis < 0L || latencyMillis > 120000L)) {
            throw new IllegalArgumentException("latencyMillis 超过允许范围");
        }
        this.latencyMillis = latencyMillis;
    }

    public static AiModerationResult manual(String reasonCode) {
        return new AiModerationResult(
                AiModerationDecision.MANUAL,
                0.0d,
                reasonCode,
                Collections.<String>emptyList(),
                null,
                null,
                null,
                null,
                null);
    }

    public AiModerationResult asManual(String manualReasonCode) {
        return new AiModerationResult(
                AiModerationDecision.MANUAL,
                confidence,
                manualReasonCode,
                riskLabels,
                category,
                priority,
                providerRequestId,
                model,
                latencyMillis);
    }

    public AiModerationDecision getDecision() {
        return decision;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public List<String> getRiskLabels() {
        return riskLabels;
    }

    public String getCategory() {
        return category;
    }

    public AiModerationPriority getPriority() {
        return priority;
    }

    public String getProviderRequestId() {
        return providerRequestId;
    }

    public String getModel() {
        return model;
    }

    public Long getLatencyMillis() {
        return latencyMillis;
    }

    private static List<String> immutableLabels(List<String> values) {
        if (values == null) {
            throw new IllegalArgumentException("riskLabels 不能为空");
        }
        if (values.size() > 16) {
            throw new IllegalArgumentException("riskLabels 超过数量上限");
        }
        List<String> checked = new ArrayList<>(values.size());
        for (String value : values) {
            String label = requireCode(value, "riskLabel");
            if (checked.contains(label)) {
                throw new IllegalArgumentException("riskLabels 不能重复");
            }
            checked.add(label);
        }
        return Collections.unmodifiableList(checked);
    }

    private static String requireCode(String value, String field) {
        if (value == null || !CODE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " 格式非法");
        }
        return value;
    }

    private static String boundedOptional(String value, int maxLength, String field) {
        if (value == null) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " 超过长度上限");
        }
        return value;
    }
}
