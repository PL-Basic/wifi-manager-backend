package com.plagod.ai.provider;

import com.plagod.ai.model.AiModerationRequest;
import com.plagod.ai.model.AiModerationResult;
import com.plagod.ai.model.AiProviderAvailability;

public final class DeterministicTestAiModerationProvider
        implements AiModerationProvider {

    private final String providerCode;
    private final AiProviderAvailability availability;
    private final AiModerationResult result;
    private final RuntimeException failure;
    private AiModerationRequest lastRequest;

    private DeterministicTestAiModerationProvider(
            String providerCode,
            AiProviderAvailability availability,
            AiModerationResult result,
            RuntimeException failure) {
        this.providerCode = providerCode;
        this.availability = availability;
        this.result = result;
        this.failure = failure;
    }

    public static DeterministicTestAiModerationProvider returns(
            AiModerationResult result) {
        return new DeterministicTestAiModerationProvider(
                "test-provider",
                AiProviderAvailability.AVAILABLE,
                result,
                null);
    }

    public static DeterministicTestAiModerationProvider fails(
            RuntimeException failure) {
        return new DeterministicTestAiModerationProvider(
                "test-provider",
                AiProviderAvailability.AVAILABLE,
                null,
                failure);
    }

    public static DeterministicTestAiModerationProvider unavailable() {
        return new DeterministicTestAiModerationProvider(
                "test-provider",
                AiProviderAvailability.UNCONFIGURED,
                null,
                null);
    }

    @Override
    public String providerCode() {
        return providerCode;
    }

    @Override
    public AiProviderAvailability availability() {
        return availability;
    }

    @Override
    public AiModerationResult review(AiModerationRequest request) {
        this.lastRequest = request;
        if (failure != null) {
            throw failure;
        }
        return result;
    }

    public AiModerationRequest getLastRequest() {
        return lastRequest;
    }
}
