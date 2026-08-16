package com.plagod.configuration;

import com.plagod.health.MqttHealthIndicator;
import com.plagod.mqtt.MqttEventSubscriber;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MqttConfigurationAndHealthTest {

    private static final String USERNAME_CANARY =
            "mqtt-username-canary-secret";
    private static final String PASSWORD_CANARY =
            "mqtt-password-canary-secret";

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void mqttConfigurationRequiresTransportIdentityTopicsAndValidQos() {
        MqttProperties properties = validProperties();
        properties.setBrokerUrl(" ");
        properties.setClientId(null);
        properties.setQos(3);
        properties.setStatusTopic("");

        Set<String> invalidProperties = validator.validate(properties)
                .stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());

        assertTrue(invalidProperties.contains("brokerUrl"));
        assertTrue(invalidProperties.contains("clientId"));
        assertTrue(invalidProperties.contains("qos"));
        assertTrue(invalidProperties.contains("statusTopic"));
    }

    @Test
    void mqttConfigurationToStringNeverRendersCredentialCanaries() {
        MqttProperties properties = validProperties();
        properties.setUsername(USERNAME_CANARY);
        properties.setPassword(PASSWORD_CANARY);

        String rendered = properties.toString();

        assertFalse(rendered.contains(USERNAME_CANARY));
        assertFalse(rendered.contains(PASSWORD_CANARY));
        assertFalse(rendered.contains(properties.getBrokerUrl()));
        assertTrue(rendered.contains("credentialsConfigured=true"));
    }

    @Test
    void applicationHealthGroupsSeparateLivenessFromDeviceReadiness()
            throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "device-application",
                new ClassPathResource("application.yml"));
        PropertySource<?> source = sources.get(0);

        assertEquals(
                "livenessState",
                source.getProperty(
                        "management.endpoint.health.group.liveness.include"));
        assertEquals(
                "readinessState,db,mqtt",
                source.getProperty(
                        "management.endpoint.health.group.readiness.include"));
    }

    @Test
    void mqttHealthIndicatorOnlyReadsSubscriberConnectionState() {
        MqttEventSubscriber subscriber = mock(MqttEventSubscriber.class);
        MqttHealthIndicator indicator = new MqttHealthIndicator(subscriber);

        when(subscriber.isConnected()).thenReturn(false, true);

        assertEquals(Status.DOWN, indicator.health().getStatus());
        assertEquals(Status.UP, indicator.health().getStatus());
    }

    private MqttProperties validProperties() {
        MqttProperties properties = new MqttProperties();
        properties.setBrokerUrl("tcp://localhost:1883");
        properties.setClientId("device-service");
        properties.setQos(1);
        properties.setStatusTopic("wifi/device/+/event/status");
        properties.setTrafficTopic("wifi/device/+/event/traffic");
        properties.setCommandResultTopic(
                "wifi/device/+/event/command-result");
        properties.setClientSignalTopic(
                "wifi/device/+/event/client-signal");
        properties.setClientDisconnectTopic(
                "wifi/device/+/event/client-disconnect");
        return properties;
    }
}
