package com.plagod.ai.provider;

import com.plagod.ai.model.AiModerationResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiProviderRegistryTest {

    @Test
    void defaultsToUnavailableWhenNoProviderIsSelected() {
        AiProviderRegistry registry =
                new AiProviderRegistry(null, Collections.emptyList());

        assertFalse(registry.selected().isPresent());
    }

    @Test
    void normalizesSelectionAndRejectsDuplicateProviderCodes() {
        AiModerationResult result = AiModerationResult.manual("TEST_MANUAL");
        DeterministicTestAiModerationProvider first =
                DeterministicTestAiModerationProvider.returns(result);
        AiProviderRegistry registry =
                new AiProviderRegistry(" TEST-PROVIDER ", Collections.singleton(first));

        assertSame(first, registry.selected().orElse(null));

        assertThrows(
                IllegalStateException.class,
                () -> new AiProviderRegistry(
                        "test-provider",
                        Arrays.asList(
                                first,
                                DeterministicTestAiModerationProvider.returns(result))));
    }
}
