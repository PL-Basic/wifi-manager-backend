package com.plagod.ai.provider;

import com.plagod.ai.model.AiModerationRequest;
import com.plagod.ai.model.AiModerationResult;
import com.plagod.ai.model.AiProviderAvailability;

public interface AiModerationProvider {

    String providerCode();

    AiProviderAvailability availability();

    AiModerationResult review(AiModerationRequest request);
}
