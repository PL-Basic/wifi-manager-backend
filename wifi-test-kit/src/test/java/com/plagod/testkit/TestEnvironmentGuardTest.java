package com.plagod.testkit;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestEnvironmentGuardTest {

    @Test
    void acceptsOnlyExplicitTestSchemaByDefault() {
        assertEquals(
                "wifi_test_wm-stage-case",
                TestEnvironmentGuard.requireSafeMySqlSchema(
                        "jdbc:mysql://localhost:3306/wifi_test_wm-stage-case?useSSL=false"));
        assertThrows(
                IllegalArgumentException.class,
                () -> TestEnvironmentGuard.requireSafeMySqlSchema(
                        "jdbc:mysql://localhost:3306/wifi"));
        assertThrows(
                IllegalArgumentException.class,
                () -> TestEnvironmentGuard.requireSafeMySqlSchema(
                        "jdbc:mysql://localhost:3306/wifi_production_copy"));
        assertThrows(
                IllegalArgumentException.class,
                () -> TestEnvironmentGuard.requireSafeMySqlSchema(
                        "jdbc:mysql://user:secret@localhost:3306/wifi_test_case"));
    }

    @Test
    void externalWritesRequireDedicatedMarkerAndExplicitFlag() {
        Map<String, String> environment = new HashMap<>();
        environment.put("WIFI_TEST_ALLOW_WRITES", "true");
        environment.put("WIFI_TEST_ACCOUNT_MARKER", "dedicated-test-account");

        TestEnvironmentGuard.requireDedicatedWriteAccess(environment);

        environment.put("WIFI_TEST_ACCOUNT_MARKER", "personal-account");
        assertThrows(
                IllegalStateException.class,
                () -> TestEnvironmentGuard.requireDedicatedWriteAccess(environment));
    }

    @Test
    void externalEndpointsRequireExplicitEnableFlagsAndSafeUris() {
        Map<String, String> environment = new HashMap<>();
        environment.put("WIFI_TEST_HTTP_ENABLED", "false");

        assertThrows(
                IllegalStateException.class,
                () -> TestEnvironmentGuard.requireEnabled(
                        environment,
                        "WIFI_TEST_HTTP_ENABLED"));
        assertThrows(
                IllegalArgumentException.class,
                () -> TestEnvironmentGuard.requireHttpBaseUri(
                        "https://user:secret@example.test"));
        assertThrows(
                IllegalArgumentException.class,
                () -> TestEnvironmentGuard.requireMqttBrokerUri(
                        "file:///tmp/broker"));
    }
}
