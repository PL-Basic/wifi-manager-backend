package com.plagod.ai.health;

import com.plagod.ai.model.AiProviderAvailability;
import com.plagod.ai.provider.AiModerationProvider;
import com.plagod.ai.provider.AiProviderRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.Optional;

public final class AiProviderHealthIndicator implements HealthIndicator {

    private final AiProviderRegistry providerRegistry;

    public AiProviderHealthIndicator(AiProviderRegistry providerRegistry) {
        if (providerRegistry == null) {
            throw new IllegalArgumentException("providerRegistry 不能为空");
        }
        this.providerRegistry = providerRegistry;
    }

    @Override
    public Health health() {
        Optional<AiModerationProvider> selected = providerRegistry.selected();
        if (!selected.isPresent()) {
            return unavailable(AiProviderAvailability.UNCONFIGURED);
        }
        AiProviderAvailability availability;
        try {
            availability = selected.get().availability();
        } catch (RuntimeException exception) {
            availability = AiProviderAvailability.UNCONFIGURED;
        }
        if (availability == AiProviderAvailability.AVAILABLE) {
            return Health.up()
                    .withDetail("availability", availability.name())
                    .build();
        }
        return unavailable(availability);
    }

    private Health unavailable(AiProviderAvailability availability) {
        String value = availability == null
                ? AiProviderAvailability.UNCONFIGURED.name()
                : availability.name();
        return Health.unknown()
                .withDetail("availability", value)
                .build();
    }
}
