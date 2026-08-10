package com.plagod.testkit;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class MqttTestClient implements AutoCloseable {

    private static final int CONNECTION_TIMEOUT_SECONDS = 10;
    private static final int KEEP_ALIVE_SECONDS = 15;

    private final String brokerUri;
    private final String clientId;
    private final String username;
    private final char[] password;
    private final TestAccessMode accessMode;

    private MqttClient client;

    private MqttTestClient(
            String brokerUri,
            String clientId,
            String username,
            char[] password,
            TestAccessMode accessMode) {
        this.brokerUri = brokerUri;
        this.clientId = clientId;
        this.username = username;
        this.password = password;
        this.accessMode = accessMode;
    }

    public static MqttTestClient create(
            Map<String, String> environment,
            String runId,
            TestAccessMode accessMode) {

        Objects.requireNonNull(accessMode, "accessMode");
        TestEnvironmentGuard.requireEnabled(environment, "WIFI_TEST_MQTT_ENABLED");
        TestEnvironmentGuard.requireVariables(environment, "WIFI_TEST_MQTT_BROKER_URI");
        if (accessMode.allowsWrites()) {
            TestEnvironmentGuard.requireDedicatedWriteAccess(environment);
        }

        String brokerUri = TestEnvironmentGuard.requireMqttBrokerUri(
                environment.get("WIFI_TEST_MQTT_BROKER_URI")).toString();
        String username = text(environment.get("WIFI_TEST_MQTT_USERNAME"));
        String passwordValue = text(environment.get("WIFI_TEST_MQTT_PASSWORD"));
        if ((username == null) != (passwordValue == null)) {
            throw new IllegalStateException(
                    "MQTT username and password must be provided together");
        }

        return new MqttTestClient(
                brokerUri,
                clientId(runId),
                username,
                passwordValue == null ? new char[0] : passwordValue.toCharArray(),
                accessMode);
    }

    public synchronized void connect() throws MqttException {
        if (client != null && client.isConnected()) {
            return;
        }

        MqttClient candidate = new MqttClient(
                brokerUri,
                clientId,
                new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        options.setConnectionTimeout(CONNECTION_TIMEOUT_SECONDS);
        options.setKeepAliveInterval(KEEP_ALIVE_SECONDS);
        if (username != null) {
            options.setUserName(username);
            options.setPassword(Arrays.copyOf(password, password.length));
        }

        boolean connected = false;
        try {
            candidate.connect(options);
            connected = true;
            client = candidate;
        } finally {
            if (!connected) {
                candidate.close();
            }
        }
    }

    public synchronized void subscribe(String topic, int qos) throws MqttException {
        requireConnected();
        client.subscribe(requireTopic(topic), requireQos(qos));
    }

    public synchronized void publish(
            String topic,
            String payload,
            int qos,
            boolean retained) throws MqttException {

        if (!accessMode.allowsWrites()) {
            throw new IllegalStateException("MQTT publish requires WRITE test access");
        }
        requireConnected();

        MqttMessage message = new MqttMessage(
                Objects.requireNonNull(payload, "payload")
                        .getBytes(StandardCharsets.UTF_8));
        message.setQos(requireQos(qos));
        message.setRetained(retained);
        client.publish(requireTopic(topic), message);
    }

    public synchronized boolean isConnected() {
        return client != null && client.isConnected();
    }

    @Override
    public synchronized void close() throws MqttException {
        MqttException failure = null;
        if (client != null) {
            try {
                if (client.isConnected()) {
                    client.disconnect(1_000L);
                }
            } catch (MqttException exception) {
                failure = exception;
            }

            try {
                client.close();
            } catch (MqttException exception) {
                if (failure == null) {
                    failure = exception;
                }
            } finally {
                client = null;
            }
        }
        Arrays.fill(password, '\0');
        if (failure != null) {
            throw failure;
        }
    }

    private void requireConnected() {
        if (!isConnected()) {
            throw new IllegalStateException("MQTT test client is not connected");
        }
    }

    private static String clientId(String runId) {
        if (runId == null || runId.trim().isEmpty()) {
            throw new IllegalArgumentException("runId is required");
        }
        String normalized = runId.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "-");
        String value = "wm-test-" + normalized;
        return value.length() <= 48 ? value : value.substring(0, 48);
    }

    private static String requireTopic(String topic) {
        String normalized = text(topic);
        if (normalized == null || normalized.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("MQTT topic is required");
        }
        return normalized;
    }

    private static int requireQos(int qos) {
        if (qos < 0 || qos > 2) {
            throw new IllegalArgumentException("MQTT QoS must be between 0 and 2");
        }
        return qos;
    }

    private static String text(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
