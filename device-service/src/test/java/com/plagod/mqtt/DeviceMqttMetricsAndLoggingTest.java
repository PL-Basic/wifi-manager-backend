package com.plagod.mqtt;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.configuration.MqttProperties;
import com.plagod.metrics.DeviceMqttMetrics;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceMqttMetricsAndLoggingTest {

    private static final String METRIC = "wifi.device.mqtt.events";
    private static final String CANARY_SECRET = "mqtt-payload-canary-secret";

    @Test
    void mqttMetricsUseOnlyFixedLowCardinalityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DeviceMqttMetrics metrics = new DeviceMqttMetrics(registry);

        metrics.recordAccepted("wifi/device/device-a/event/status");
        metrics.recordAccepted("wifi/device/device-b/event/status");
        metrics.recordRejected("wifi/device/device-c/event/command-result");
        metrics.recordRejected("wifi/device/device-d/event/unregistered");
        metrics.recordConnectionState(true);
        metrics.recordPublishSuccess(10L);
        metrics.recordPublishFailure(20L);

        assertEquals(2.0, registry.get(METRIC)
                .tags("event", "status", "outcome", "accepted")
                .counter().count());

        for (Meter meter : registry.getMeters()) {
            Set<String> tagKeys = new HashSet<>();
            for (Tag tag : meter.getId().getTags()) {
                tagKeys.add(tag.getKey());
                assertFalse(tag.getValue().contains("device-"));
                assertFalse(tag.getKey().equals("deviceCode"));
                assertFalse(tag.getKey().equals("topic"));
                assertFalse(tag.getKey().equals("requestId"));
            }
            assertTrue(
                    new HashSet<>(java.util.Arrays.asList("event", "outcome"))
                            .containsAll(tagKeys));
        }

        assertEquals(1.0, registry.get("wifi.device.mqtt.connected")
                .gauge().value());
        assertEquals(1.0, registry.get("wifi.device.mqtt.publish")
                .tag("outcome", "success").counter().count());
        assertEquals(1.0, registry.get("wifi.device.mqtt.publish")
                .tag("outcome", "failure").counter().count());
    }

    @Test
    void publisherRecordsSuccessAndFailureWithoutHighCardinalityTags()
            throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DeviceMqttMetrics metrics = new DeviceMqttMetrics(registry);
        MqttProperties properties = mqttProperties();

        MqttClient successfulClient = mock(MqttClient.class);
        MqttCommandPublisher successfulPublisher =
                publisher(properties, metrics, successfulClient);
        successfulPublisher.publish("wifi/device/device-a/cmd/allow", "{}");

        MqttClient failedClient = mock(MqttClient.class);
        doThrow(new org.eclipse.paho.client.mqttv3.MqttException(
                org.eclipse.paho.client.mqttv3.MqttException
                        .REASON_CODE_CLIENT_EXCEPTION))
                .when(failedClient)
                .publish(anyString(), any(MqttMessage.class));
        MqttCommandPublisher failedPublisher =
                publisher(properties, metrics, failedClient);

        assertThrows(
                IllegalStateException.class,
                () -> failedPublisher.publish(
                        "wifi/device/device-b/cmd/allow", "{}"));

        assertEquals(1.0, registry.get("wifi.device.mqtt.publish")
                .tag("outcome", "success").counter().count());
        assertEquals(1.0, registry.get("wifi.device.mqtt.publish")
                .tag("outcome", "failure").counter().count());
    }

    @Test
    void malformedMqttPayloadNeverLeaksCanaryIntoLogs() {
        MqttEventSubscriber subscriber = new MqttEventSubscriber();
        DeviceMqttMetrics metrics = mock(DeviceMqttMetrics.class);
        ReflectionTestUtils.setField(subscriber, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(subscriber, "mqttMetrics", metrics);

        Logger logger = (Logger) LoggerFactory.getLogger(MqttEventSubscriber.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        String topic = "wifi/device/esp32-gateway-001/event/status";
        String malformed = "{\"password\":\"" + CANARY_SECRET + "\"";

        try {
            ReflectionTestUtils.invokeMethod(
                    subscriber,
                    "handleMessage",
                    topic,
                    new MqttMessage(malformed.getBytes(StandardCharsets.UTF_8)));

            verify(metrics).recordRejected(topic);
            for (ILoggingEvent event : appender.list) {
                assertFalse(event.getFormattedMessage().contains(CANARY_SECRET));
                assertFalse(event.getFormattedMessage().contains("password"));
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void publisherCleanupUsesSharedSafeExceptionRendering()
            throws Exception {
        MqttCommandPublisher publisher = new MqttCommandPublisher();
        MqttClient client = mock(MqttClient.class);
        when(client.isConnected()).thenReturn(true);
        doThrow(new IllegalStateException(CANARY_SECRET))
                .when(client).disconnect();

        Logger logger = (Logger) LoggerFactory.getLogger(
                MqttCommandPublisher.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            ReflectionTestUtils.invokeMethod(
                    publisher, "closeQuietly", client);

            assertFalse(appender.list.isEmpty());
            for (ILoggingEvent event : appender.list) {
                assertFalse(event.getFormattedMessage()
                        .contains(CANARY_SECRET));
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private MqttCommandPublisher publisher(
            MqttProperties properties,
            DeviceMqttMetrics metrics,
            MqttClient client) throws Exception {
        MqttCommandPublisher publisher = spy(new MqttCommandPublisher());
        ReflectionTestUtils.setField(
                publisher, "mqttProperties", properties);
        ReflectionTestUtils.setField(publisher, "mqttMetrics", metrics);
        doReturn(client).when(publisher)
                .createClient(anyString(), anyString());
        return publisher;
    }

    private MqttProperties mqttProperties() {
        MqttProperties properties = new MqttProperties();
        properties.setBrokerUrl("tcp://localhost:1883");
        properties.setClientId("device-service");
        properties.setQos(1);
        properties.setRetained(false);
        return properties;
    }
}
