package com.plagod.health;

import com.plagod.mqtt.MqttEventSubscriber;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class MqttHealthIndicator implements HealthIndicator {

    private final MqttEventSubscriber mqttEventSubscriber;

    public MqttHealthIndicator(MqttEventSubscriber mqttEventSubscriber) {
        this.mqttEventSubscriber = mqttEventSubscriber;
    }

    @Override
    public Health health() {
        return mqttEventSubscriber.isConnected()
                ? Health.up().build()
                : Health.down().build();
    }
}
