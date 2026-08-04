package com.plagod.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrustedRequestPropertiesTest {

    @Test
    void productionRejectsLocalPlaceholderToken() {
        TrustedRequestProperties properties = properties(
                "local-gateway-token-change-me",
                "a-valid-internal-token-value");

        assertThrows(IllegalStateException.class,
                () -> properties.validateProduction("a-valid-internal-token-value"));
    }

    @Test
    void productionRequiresInternalToken() {
        TrustedRequestProperties properties = properties(
                "a-valid-gateway-token-value",
                null);

        properties.setInternalTokenRequired(true);

        assertThrows(IllegalStateException.class,
                () -> properties.validateProduction("a-valid-internal-token-value"));
    }

    @Test
    void productionRejectsEqualTokens() {
        TrustedRequestProperties properties = properties(
                "a-shared-production-token",
                "a-shared-production-token");

        assertThrows(IllegalStateException.class,
                () -> properties.validateProduction("a-shared-production-token"));
    }

    @Test
    void productionAcceptsDistinctNonPlaceholderTokens() {
        TrustedRequestProperties properties = properties(
                "a-valid-gateway-token-value",
                "a-valid-internal-token-value");

        properties.setInternalTokenRequired(true);

        assertDoesNotThrow(() -> properties.validateProduction("a-valid-internal-token-value"));
    }

    @Test
    void productionServiceWithoutInternalEndpointRejectsInboundInternalToken() {
        TrustedRequestProperties properties = properties(
                "a-valid-gateway-token-value",
                "a-valid-internal-token-value");

        assertThrows(IllegalStateException.class,
                () -> properties.validateProduction("a-valid-internal-token-value"));
    }

    @Test
    void productionServiceWithoutInternalEndpointAcceptsOutboundTokenOnly() {
        TrustedRequestProperties properties = properties(
                "a-valid-gateway-token-value",
                null);

        assertDoesNotThrow(() -> properties.validateProduction("a-valid-internal-token-value"));
    }

    @Test
    void productionRejectsDisabledTrustedRequestAuthentication() {
        TrustedRequestProperties properties = properties(
                "a-valid-gateway-token-value",
                null);
        properties.setEnabled(false);

        assertThrows(IllegalStateException.class,
                () -> properties.validateProduction("a-valid-internal-token-value"));
    }

    private TrustedRequestProperties properties(String gatewayToken, String internalToken) {
        TrustedRequestProperties properties = new TrustedRequestProperties();
        properties.setGatewayToken(gatewayToken);
        properties.setInternalToken(internalToken);
        return properties;
    }
}
