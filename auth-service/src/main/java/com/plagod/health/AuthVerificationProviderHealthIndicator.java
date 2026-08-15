package com.plagod.health;

import com.plagod.configuration.PhoneVerificationProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class AuthVerificationProviderHealthIndicator
        implements HealthIndicator {

    private final PhoneVerificationProperties properties;

    public AuthVerificationProviderHealthIndicator(
            PhoneVerificationProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        String provider = properties.getProvider();
        if (properties.isSelectedProviderConfigured()) {
            return Health.up()
                    .withDetail("provider", provider)
                    .withDetail("availability", "AVAILABLE")
                    .build();
        }
        return Health.unknown()
                .withDetail("provider", provider)
                .withDetail("availability", "UNCONFIGURED")
                .build();
    }
}
