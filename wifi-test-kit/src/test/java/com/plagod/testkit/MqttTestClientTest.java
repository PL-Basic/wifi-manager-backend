package com.plagod.testkit;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MqttTestClientTest {

    @Test
    void missingEnvironmentFailsWithoutOpeningNetwork() {
        assertThrows(
                IllegalStateException.class,
                () -> MqttTestClient.create(
                        new HashMap<>(),
                        "wm-stage-e-case-123456-abc123",
                        TestAccessMode.READ_ONLY));
    }

    @Test
    void readOnlyClientRejectsPublishBeforeOpeningNetwork() throws Exception {
        try (MqttTestClient client = MqttTestClient.create(
                mqttEnvironment(),
                "wm-stage-e-case-123456-abc123",
                TestAccessMode.READ_ONLY)) {

            assertThrows(
                    IllegalStateException.class,
                    () -> client.publish(
                            "wifi/device/test/cmd/kick",
                            "{}",
                            1,
                            false));
        }
    }

    @Test
    void unsupportedBrokerSchemeFailsWithoutOpeningNetwork() {
        Map<String, String> environment = mqttEnvironment();
        environment.put("WIFI_TEST_MQTT_BROKER_URI", "file:///tmp/mqtt");

        assertThrows(
                IllegalArgumentException.class,
                () -> MqttTestClient.create(
                        environment,
                        "wm-stage-e-case-123456-abc123",
                        TestAccessMode.READ_ONLY));
    }

    @Test
    void writeClientRequiresDedicatedAccount() {
        assertThrows(
                IllegalStateException.class,
                () -> MqttTestClient.create(
                        mqttEnvironment(),
                        "wm-stage-e-case-123456-abc123",
                        TestAccessMode.WRITE));
    }

    private static Map<String, String> mqttEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("WIFI_TEST_MQTT_ENABLED", "true");
        environment.put("WIFI_TEST_MQTT_BROKER_URI", "tcp://127.0.0.1:1883");
        return environment;
    }
}
