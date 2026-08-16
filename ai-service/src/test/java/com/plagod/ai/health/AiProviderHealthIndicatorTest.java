package com.plagod.ai.health;

import com.plagod.ai.model.AiModerationResult;
import com.plagod.ai.provider.AiProviderRegistry;
import com.plagod.ai.provider.DeterministicTestAiModerationProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AiProviderHealthIndicatorTest {

    @Test
    void reportsUnconfiguredWithoutLeakingProviderConfiguration() {
        AiProviderHealthIndicator indicator =
                new AiProviderHealthIndicator(
                        new AiProviderRegistry(
                                null,
                                Collections.emptyList()));

        Health health = indicator.health();

        assertEquals(Status.UNKNOWN, health.getStatus());
        assertEquals(
                "UNCONFIGURED",
                health.getDetails().get("availability"));
        assertFalse(health.toString().contains("api-key"));
        assertFalse(health.toString().contains("endpoint"));
    }

    @Test
    void reportsAvailableProviderAsUpUsingOnlyBoundedStatus() {
        DeterministicTestAiModerationProvider provider =
                DeterministicTestAiModerationProvider.returns(
                        AiModerationResult.manual("TEST_MANUAL"));
        AiProviderHealthIndicator indicator =
                new AiProviderHealthIndicator(
                        new AiProviderRegistry(
                                provider.providerCode(),
                                Collections.singleton(provider)));

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(
                "AVAILABLE",
                health.getDetails().get("availability"));
        assertEquals(1, health.getDetails().size());
    }
}
