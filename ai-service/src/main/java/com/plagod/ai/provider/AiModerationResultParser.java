package com.plagod.ai.provider;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.ai.model.AiModerationDecision;
import com.plagod.ai.model.AiModerationPriority;
import com.plagod.ai.model.AiModerationResult;
import com.plagod.ai.model.AiModerationScene;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class AiModerationResultParser {

    public static final String SCHEMA_VERSION = "ai-moderation-result-v1";

    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    private static final Set<String> REQUIRED_FIELDS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "schemaVersion",
                    "decision",
                    "confidence",
                    "reasonCode",
                    "riskLabels")));
    private static final Set<String> ALLOWED_FIELDS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "schemaVersion",
                    "decision",
                    "confidence",
                    "reasonCode",
                    "riskLabels",
                    "category",
                    "priority",
                    "providerRequestId",
                    "model",
                    "latencyMillis")));

    private final ObjectMapper objectMapper;
    private final int maxResponseBytes;

    public AiModerationResultParser(ObjectMapper objectMapper, int maxResponseBytes) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper 不能为空");
        }
        if (maxResponseBytes < 1024 || maxResponseBytes > 262144) {
            throw new IllegalArgumentException("maxResponseBytes 超过允许范围");
        }
        this.objectMapper = objectMapper.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.maxResponseBytes = maxResponseBytes;
    }

    public AiModerationResult parse(String rawResponse, AiModerationScene scene) {
        if (rawResponse == null || scene == null) {
            throw AiProviderException.responseInvalid();
        }
        if (rawResponse.getBytes(StandardCharsets.UTF_8).length > maxResponseBytes) {
            throw AiProviderException.responseTooLarge();
        }
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            validateObject(root);

            AiModerationDecision decision = parseDecision(root.get("decision"));
            double confidence = parseConfidence(root.get("confidence"));
            String reasonCode = parseCode(root.get("reasonCode"));
            List<String> riskLabels = parseLabels(root.get("riskLabels"));
            String category = parseOptionalText(root.get("category"), 64);
            AiModerationPriority priority = parsePriority(root.get("priority"));
            String providerRequestId =
                    parseOptionalText(root.get("providerRequestId"), 128);
            String model = parseOptionalText(root.get("model"), 96);
            Long latencyMillis = parseLatency(root.get("latencyMillis"));

            if (scene == AiModerationScene.ANNOUNCEMENT_REVIEW
                    && (category != null || priority != null)) {
                throw AiProviderException.responseInvalid();
            }

            return new AiModerationResult(
                    decision,
                    confidence,
                    reasonCode,
                    riskLabels,
                    category,
                    priority,
                    providerRequestId,
                    model,
                    latencyMillis);
        } catch (AiProviderException exception) {
            throw exception;
        } catch (RuntimeException | java.io.IOException exception) {
            throw AiProviderException.responseInvalid();
        }
    }

    private void validateObject(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw AiProviderException.responseInvalid();
        }
        Set<String> actualFields = new HashSet<>();
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            actualFields.add(names.next());
        }
        if (!actualFields.containsAll(REQUIRED_FIELDS)
                || !ALLOWED_FIELDS.containsAll(actualFields)) {
            throw AiProviderException.responseInvalid();
        }
        JsonNode schemaVersion = root.get("schemaVersion");
        if (schemaVersion == null
                || !schemaVersion.isTextual()
                || !SCHEMA_VERSION.equals(schemaVersion.textValue())) {
            throw AiProviderException.responseInvalid();
        }
    }

    private AiModerationDecision parseDecision(JsonNode value) {
        if (value == null || !value.isTextual()) {
            throw AiProviderException.responseInvalid();
        }
        try {
            return AiModerationDecision.valueOf(value.textValue());
        } catch (IllegalArgumentException exception) {
            throw AiProviderException.responseInvalid();
        }
    }

    private double parseConfidence(JsonNode value) {
        if (value == null || !value.isNumber()) {
            throw AiProviderException.responseInvalid();
        }
        double confidence = value.doubleValue();
        if (Double.isNaN(confidence)
                || Double.isInfinite(confidence)
                || confidence < 0.0d
                || confidence > 1.0d) {
            throw AiProviderException.responseInvalid();
        }
        return confidence;
    }

    private String parseCode(JsonNode value) {
        if (value == null
                || !value.isTextual()
                || !CODE_PATTERN.matcher(value.textValue()).matches()) {
            throw AiProviderException.responseInvalid();
        }
        return value.textValue();
    }

    private List<String> parseLabels(JsonNode value) {
        if (value == null || !value.isArray() || value.size() > 16) {
            throw AiProviderException.responseInvalid();
        }
        List<String> labels = new ArrayList<>(value.size());
        for (JsonNode labelNode : value) {
            String label = parseCode(labelNode);
            if (labels.contains(label)) {
                throw AiProviderException.responseInvalid();
            }
            labels.add(label);
        }
        return labels;
    }

    private String parseOptionalText(JsonNode value, int maxLength) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.textValue().length() > maxLength) {
            throw AiProviderException.responseInvalid();
        }
        return value.textValue();
    }

    private AiModerationPriority parsePriority(JsonNode value) {
        String priority = parseOptionalText(value, 16);
        if (priority == null) {
            return null;
        }
        try {
            return AiModerationPriority.valueOf(priority);
        } catch (IllegalArgumentException exception) {
            throw AiProviderException.responseInvalid();
        }
    }

    private Long parseLatency(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber()) {
            throw AiProviderException.responseInvalid();
        }
        if (!value.canConvertToLong()) {
            throw AiProviderException.responseInvalid();
        }
        long latency = value.longValue();
        if (latency < 0L || latency > 120000L) {
            throw AiProviderException.responseInvalid();
        }
        return latency;
    }
}
