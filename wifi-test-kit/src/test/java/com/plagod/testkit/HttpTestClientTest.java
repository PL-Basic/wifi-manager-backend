package com.plagod.testkit;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpTestClientTest {

    @Test
    void missingEnvironmentFailsWithoutOpeningNetwork() {
        assertThrows(
                IllegalStateException.class,
                () -> HttpTestClient.create(
                        new HashMap<>(),
                        TestAccessMode.READ_ONLY));
    }

    @Test
    void readOnlyClientRejectsWritesBeforeOpeningNetwork() throws Exception {
        try (HttpTestClient client = HttpTestClient.create(
                httpEnvironment(),
                TestAccessMode.READ_ONLY)) {

            assertThrows(
                    IllegalStateException.class,
                    () -> client.request(
                            "POST",
                            "/admin/devices",
                            "{}",
                            Collections.emptyMap()));
        }
    }

    @Test
    void clientRejectsAbsoluteRequestUrisBeforeOpeningNetwork() throws Exception {
        try (HttpTestClient client = HttpTestClient.create(
                httpEnvironment(),
                TestAccessMode.READ_ONLY)) {

            assertThrows(
                    IllegalArgumentException.class,
                    () -> client.get(
                            "https://other.example.test/admin/devices",
                            Collections.emptyMap()));
        }
    }

    @Test
    void writeClientRequiresDedicatedAccount() {
        assertThrows(
                IllegalStateException.class,
                () -> HttpTestClient.create(
                        httpEnvironment(),
                        TestAccessMode.WRITE));
    }

    private static Map<String, String> httpEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("WIFI_TEST_HTTP_ENABLED", "true");
        environment.put("WIFI_TEST_HTTP_BASE_URL", "http://127.0.0.1:1");
        environment.put("WIFI_TEST_HTTP_TIMEOUT_MILLIS", "100");
        return environment;
    }
}
