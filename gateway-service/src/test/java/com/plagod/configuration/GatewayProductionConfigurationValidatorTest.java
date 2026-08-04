package com.plagod.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GatewayProductionConfigurationValidatorTest {

    @Test
    void rejectsLocalPlaceholderToken() {
        GatewayProductionConfigurationValidator validator =
                new GatewayProductionConfigurationValidator("local-gateway-token-change-me", true);

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void rejectsDisabledRedisRateLimit() {
        GatewayProductionConfigurationValidator validator =
                new GatewayProductionConfigurationValidator("a-valid-gateway-token-value", false);

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void acceptsProductionSecurityConfiguration() {
        GatewayProductionConfigurationValidator validator =
                new GatewayProductionConfigurationValidator("a-valid-gateway-token-value", true);

        assertDoesNotThrow(validator::validate);
    }
}
