package com.plagod.ai.service;

import com.plagod.ai.model.AiModerationDecision;
import com.plagod.ai.model.AiModerationRequest;
import com.plagod.ai.model.AiModerationResult;
import com.plagod.ai.model.AiModerationScene;
import com.plagod.ai.model.AiProviderAvailability;
import com.plagod.ai.observability.AiModerationMetricOutcome;
import com.plagod.ai.observability.AiProviderMetrics;
import com.plagod.ai.provider.AiModerationProvider;
import com.plagod.ai.provider.AiProviderException;
import com.plagod.ai.provider.AiProviderFailure;
import com.plagod.ai.provider.AiProviderRegistry;
import com.plagod.ai.support.AiContentSecurity;
import com.plagod.support.StructuredRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class AiModerationService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AiModerationService.class);
    private static final Set<String> SAFE_LOG_KEYS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "providerCode",
                    "reviewRequestId",
                    "reasonCode")));

    private final AiProviderRegistry providerRegistry;
    private final AiContentSecurity contentSecurity;
    private final AiProviderMetrics metrics;

    public AiModerationService(
            AiProviderRegistry providerRegistry,
            AiContentSecurity contentSecurity,
            AiProviderMetrics metrics) {
        if (providerRegistry == null
                || contentSecurity == null
                || metrics == null) {
            throw new IllegalArgumentException("AI 审核依赖不能为空");
        }
        this.providerRegistry = providerRegistry;
        this.contentSecurity = contentSecurity;
        this.metrics = metrics;
    }

    public AiModerationResult review(
            AiModerationRequest request,
            double minimumConfidence) {
        long startedAtNanos = System.nanoTime();
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        if (Double.isNaN(minimumConfidence)
                || Double.isInfinite(minimumConfidence)
                || minimumConfidence < 0.0d
                || minimumConfidence > 1.0d) {
            throw new IllegalArgumentException("minimumConfidence 必须在 0..1");
        }

        if (!contentSecurity.hashMatches(request)) {
            return fallback(
                    providerRegistry.selectedProviderCode()
                            .orElse("unconfigured"),
                    request,
                    "CONTENT_HASH_MISMATCH",
                    AiModerationMetricOutcome.CONTENT_HASH_MISMATCH,
                    startedAtNanos);
        }

        Optional<AiModerationProvider> selected = providerRegistry.selected();
        String providerCode = providerRegistry.selectedProviderCode()
                .orElse("unconfigured");
        if (!selected.isPresent()) {
            return fallback(
                    providerCode,
                    request,
                    "AI_PROVIDER_UNAVAILABLE",
                    AiModerationMetricOutcome.PROVIDER_UNAVAILABLE,
                    startedAtNanos);
        }

        AiModerationProvider provider = selected.get();
        try {
            if (provider.availability() != AiProviderAvailability.AVAILABLE) {
                return fallback(
                        providerCode,
                        request,
                        "AI_PROVIDER_UNAVAILABLE",
                        AiModerationMetricOutcome.PROVIDER_UNAVAILABLE,
                        startedAtNanos);
            }
            AiModerationRequest outbound =
                    contentSecurity.redactForOutbound(request);
            AiModerationResult result = provider.review(outbound);
            if (result == null) {
                return fallback(
                        providerCode,
                        request,
                        "AI_RESPONSE_INVALID",
                        AiModerationMetricOutcome.RESPONSE_INVALID,
                        startedAtNanos);
            }
            if (request.getScene() == AiModerationScene.ANNOUNCEMENT_REVIEW
                    && (result.getCategory() != null || result.getPriority() != null)) {
                return fallback(
                        providerCode,
                        request,
                        "AI_RESPONSE_INVALID",
                        AiModerationMetricOutcome.RESPONSE_INVALID,
                        startedAtNanos);
            }
            if (result.getDecision() != AiModerationDecision.MANUAL
                    && result.getConfidence() < minimumConfidence) {
                logFallback(providerCode, request, "LOW_CONFIDENCE");
                AiModerationResult manual =
                        result.asManual("LOW_CONFIDENCE");
                recordMetric(
                        AiModerationMetricOutcome.LOW_CONFIDENCE,
                        startedAtNanos);
                return manual;
            }
            recordMetric(decisionOutcome(result.getDecision()), startedAtNanos);
            return result;
        } catch (AiProviderException exception) {
            return fallback(
                    providerCode,
                    request,
                    reasonFor(exception.getFailure()),
                    outcomeFor(exception.getFailure()),
                    startedAtNanos);
        } catch (RuntimeException exception) {
            return fallback(
                    providerCode,
                    request,
                    "AI_PROVIDER_UNAVAILABLE",
                    AiModerationMetricOutcome.PROVIDER_UNAVAILABLE,
                    startedAtNanos);
        }
    }

    private AiModerationResult fallback(
            String providerCode,
            AiModerationRequest request,
            String reasonCode,
            AiModerationMetricOutcome outcome,
            long startedAtNanos) {
        logFallback(providerCode, request, reasonCode);
        recordMetric(outcome, startedAtNanos);
        return AiModerationResult.manual(reasonCode);
    }

    private void logFallback(
            String providerCode,
            AiModerationRequest request,
            String reasonCode) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("providerCode", providerCode);
        metadata.put("reviewRequestId", request.getReviewRequestId());
        metadata.put("reasonCode", reasonCode);
        LOGGER.warn("AI moderation fallback: metadata={}",
                StructuredRedactor.redact(
                        metadata,
                        SAFE_LOG_KEYS,
                        Collections.<String>emptySet()));
    }

    private void recordMetric(
            AiModerationMetricOutcome outcome,
            long startedAtNanos) {
        try {
            metrics.record(
                    outcome,
                    Math.max(0L, System.nanoTime() - startedAtNanos));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "AI moderation metric recording failed: outcome={}",
                    outcome.name());
        }
    }

    private AiModerationMetricOutcome decisionOutcome(
            AiModerationDecision decision) {
        if (decision == AiModerationDecision.APPROVE) {
            return AiModerationMetricOutcome.APPROVED;
        }
        if (decision == AiModerationDecision.REJECT) {
            return AiModerationMetricOutcome.REJECTED;
        }
        return AiModerationMetricOutcome.PROVIDER_MANUAL;
    }

    private String reasonFor(AiProviderFailure failure) {
        if (failure == AiProviderFailure.RESPONSE_INVALID
                || failure == AiProviderFailure.RESPONSE_TOO_LARGE) {
            return "AI_RESPONSE_INVALID";
        }
        return "AI_PROVIDER_UNAVAILABLE";
    }

    private AiModerationMetricOutcome outcomeFor(
            AiProviderFailure failure) {
        if (failure == AiProviderFailure.RESPONSE_INVALID
                || failure == AiProviderFailure.RESPONSE_TOO_LARGE) {
            return AiModerationMetricOutcome.RESPONSE_INVALID;
        }
        return AiModerationMetricOutcome.PROVIDER_UNAVAILABLE;
    }
}
