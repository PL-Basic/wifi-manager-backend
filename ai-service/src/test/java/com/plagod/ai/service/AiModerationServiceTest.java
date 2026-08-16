package com.plagod.ai.service;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.plagod.ai.model.AiModerationDecision;
import com.plagod.ai.model.AiModerationRequest;
import com.plagod.ai.model.AiModerationResult;
import com.plagod.ai.model.AiModerationScene;
import com.plagod.ai.model.AiProviderAvailability;
import com.plagod.ai.observability.AiProviderMetrics;
import com.plagod.ai.provider.AiModerationProvider;
import com.plagod.ai.provider.AiProviderException;
import com.plagod.ai.provider.AiProviderRegistry;
import com.plagod.ai.provider.DeterministicTestAiModerationProvider;
import com.plagod.ai.support.AiContentSecurity;
import com.plagod.web.LowCardinalityTagPolicy;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.net.SocketTimeoutException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiModerationServiceTest {

    private static final String SECRET_BODY =
            "联系 user@example.com 或 13800138000，Bearer canary-secret-token";

    private final AiContentSecurity contentSecurity = new AiContentSecurity();
    private ch.qos.logback.classic.Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void attachLogAppender() {
        logger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(AiModerationService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        meterRegistry = new SimpleMeterRegistry();
        meterRegistry.config().meterFilter(new LowCardinalityTagPolicy());
    }

    @AfterEach
    void detachLogAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void returnsProviderDecisionAfterRedactingOutboundContent() {
        AiModerationResult approved = result(
                AiModerationDecision.APPROVE,
                0.96d,
                "POLICY_CLEAR");
        DeterministicTestAiModerationProvider provider =
                DeterministicTestAiModerationProvider.returns(approved);
        AiModerationService service = service(provider);

        AiModerationResult actual = service.review(request(SECRET_BODY), 0.80d);

        assertEquals(AiModerationDecision.APPROVE, actual.getDecision());
        assertNotNull(provider.getLastRequest());
        assertFalse(provider.getLastRequest().getBody().contains("user@example.com"));
        assertFalse(provider.getLastRequest().getBody().contains("13800138000"));
        assertFalse(provider.getLastRequest().getBody().contains("canary-secret-token"));
        Timer timer = meterRegistry.find(AiProviderMetrics.REVIEW_DURATION_METRIC)
                .tags(
                        "event", "review",
                        "result", "SUCCESS",
                        "type", "approve")
                .timer();
        assertNotNull(timer);
        assertEquals(1L, timer.count());
        assertFalse(timer.getId().toString().contains("review-request-1"));
    }

    @Test
    void defaultsToManualWhenProviderIsNotConfigured() {
        AiModerationService service = new AiModerationService(
                new AiProviderRegistry(null, Collections.emptyList()),
                contentSecurity,
                new AiProviderMetrics(meterRegistry));

        AiModerationResult actual = service.review(request(SECRET_BODY), 0.80d);

        assertEquals(AiModerationDecision.MANUAL, actual.getDecision());
        assertEquals("AI_PROVIDER_UNAVAILABLE", actual.getReasonCode());
    }

    @Test
    void mapsTimeoutToManualWithoutLoggingBodyOrCauseMessage() {
        DeterministicTestAiModerationProvider provider =
                DeterministicTestAiModerationProvider.fails(
                        AiProviderException.timeout(
                                new SocketTimeoutException(
                                        "provider-body-canary " + SECRET_BODY)));
        AiModerationService service = service(provider);

        AiModerationResult actual = service.review(request(SECRET_BODY), 0.80d);

        assertEquals(AiModerationDecision.MANUAL, actual.getDecision());
        assertEquals("AI_PROVIDER_UNAVAILABLE", actual.getReasonCode());
        for (ILoggingEvent event : appender.list) {
            assertFalse(event.getFormattedMessage().contains(SECRET_BODY));
            assertFalse(event.getFormattedMessage().contains("provider-body-canary"));
            assertNull(event.getThrowableProxy());
        }
    }

    @Test
    void mapsInvalidProviderResponseToManual() {
        AiModerationService service = service(
                DeterministicTestAiModerationProvider.fails(
                        AiProviderException.responseInvalid()));

        AiModerationResult actual = service.review(request("ordinary"), 0.80d);

        assertEquals(AiModerationDecision.MANUAL, actual.getDecision());
        assertEquals("AI_RESPONSE_INVALID", actual.getReasonCode());
    }

    @Test
    void mapsLowConfidenceDecisionToManualAndPreservesMetadata() {
        AiModerationResult lowConfidence = result(
                AiModerationDecision.APPROVE,
                0.69d,
                "POLICY_CLEAR");
        AiModerationService service = service(
                DeterministicTestAiModerationProvider.returns(lowConfidence));

        AiModerationResult actual = service.review(request("ordinary"), 0.70d);

        assertEquals(AiModerationDecision.MANUAL, actual.getDecision());
        assertEquals("LOW_CONFIDENCE", actual.getReasonCode());
        assertEquals(0.69d, actual.getConfidence(), 0.000001d);
        assertEquals("provider-request-1", actual.getProviderRequestId());
    }

    @Test
    void preservesExplicitManualDecisionEvenWhenConfidenceIsLow() {
        AiModerationResult providerManual = result(
                AiModerationDecision.MANUAL,
                0.20d,
                "PROVIDER_MANUAL");
        AiModerationService service = service(
                DeterministicTestAiModerationProvider.returns(providerManual));

        AiModerationResult actual = service.review(request("ordinary"), 0.80d);

        assertEquals(AiModerationDecision.MANUAL, actual.getDecision());
        assertEquals("PROVIDER_MANUAL", actual.getReasonCode());
    }

    @Test
    void rejectsContentHashMismatchBeforeCallingProvider() {
        DeterministicTestAiModerationProvider provider =
                DeterministicTestAiModerationProvider.returns(result(
                        AiModerationDecision.APPROVE,
                        0.99d,
                        "POLICY_CLEAR"));
        AiModerationService service = service(provider);
        AiModerationRequest request = new AiModerationRequest(
                AiModerationScene.ANNOUNCEMENT_REVIEW,
                "review-request-1",
                "policy-v1",
                "title",
                "ordinary",
                repeat('0', 64),
                "zh-CN");

        AiModerationResult actual = service.review(request, 0.80d);

        assertEquals(AiModerationDecision.MANUAL, actual.getDecision());
        assertEquals("CONTENT_HASH_MISMATCH", actual.getReasonCode());
        assertNull(provider.getLastRequest());
    }

    @Test
    void failClosedLoggingDoesNotReinvokeUntrustedProviderCode() {
        AiModerationProvider provider = new AiModerationProvider() {
            private int providerCodeCalls;

            @Override
            public String providerCode() {
                providerCodeCalls++;
                if (providerCodeCalls > 1) {
                    throw new IllegalStateException("provider-body-canary");
                }
                return "unstable-provider";
            }

            @Override
            public AiProviderAvailability availability() {
                throw new IllegalStateException("provider-body-canary");
            }

            @Override
            public AiModerationResult review(AiModerationRequest request) {
                return null;
            }
        };
        AiModerationService service = new AiModerationService(
                new AiProviderRegistry(
                        "unstable-provider",
                        Collections.singleton(provider)),
                contentSecurity,
                new AiProviderMetrics(meterRegistry));

        AiModerationResult actual = service.review(request(SECRET_BODY), 0.80d);

        assertEquals(AiModerationDecision.MANUAL, actual.getDecision());
        for (ILoggingEvent event : appender.list) {
            assertFalse(event.getFormattedMessage().contains(SECRET_BODY));
            assertFalse(event.getFormattedMessage().contains("provider-body-canary"));
            assertNull(event.getThrowableProxy());
        }
    }

    private AiModerationService service(
            DeterministicTestAiModerationProvider provider) {
        return new AiModerationService(
                new AiProviderRegistry(
                        provider.providerCode(),
                        Collections.singleton(provider)),
                contentSecurity,
                new AiProviderMetrics(meterRegistry));
    }

    private AiModerationRequest request(String body) {
        return new AiModerationRequest(
                AiModerationScene.ANNOUNCEMENT_REVIEW,
                "review-request-1",
                "policy-v1",
                "title",
                body,
                contentSecurity.contentHash("title", body),
                "zh-CN");
    }

    private AiModerationResult result(
            AiModerationDecision decision,
            double confidence,
            String reasonCode) {
        return new AiModerationResult(
                decision,
                confidence,
                reasonCode,
                Collections.singletonList("SAFE"),
                null,
                null,
                "provider-request-1",
                "model-v1",
                25L);
    }

    private String repeat(char value, int count) {
        StringBuilder output = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }
}
