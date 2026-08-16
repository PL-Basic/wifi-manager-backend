package com.plagod.mqtt;

import com.plagod.configuration.MqttProperties;
import com.plagod.metrics.DeviceMqttMetrics;
import com.plagod.web.SafeExceptionLogFormatter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
public class MqttCommandPublisher {

    @Autowired
    private MqttProperties mqttProperties;

    @Autowired
    private DeviceMqttMetrics mqttMetrics;

    public void publish(String topic, String payload) {
        MqttClient client = null;
        long startedNanos = System.nanoTime();

        try {
            String clientId = mqttProperties.getClientId() + "-publisher-" + UUID.randomUUID();

            client = createClient(mqttProperties.getBrokerUrl(), clientId);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            if (StringUtils.hasText(mqttProperties.getUsername())) {
                options.setUserName(mqttProperties.getUsername());
            }
            if (StringUtils.hasText(mqttProperties.getPassword())) {
                options.setPassword(mqttProperties.getPassword().toCharArray());
            }

            client.connect(options);

            client.publish(topic, createMessage(payload));
            mqttMetrics.recordPublishSuccess(System.nanoTime() - startedNanos);
        } catch (Exception exception) {
            mqttMetrics.recordPublishFailure(System.nanoTime() - startedNanos);
            throw new IllegalStateException("MQTT 命令发布失败", exception);
        } finally {
            closeQuietly(client);
        }
    }

    private void closeQuietly(MqttClient client) {
        if (client == null) {
            return;
        }

        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } catch (Exception exception) {
            log.warn(
                    "MQTT 发布客户端断开失败，type={}, safeStack={}",
                    exception.getClass().getName(),
                    SafeExceptionLogFormatter.format(exception));
        }

        try {
            client.close();
        } catch (Exception exception) {
            log.warn(
                    "MQTT 发布客户端关闭失败，type={}, safeStack={}",
                    exception.getClass().getName(),
                    SafeExceptionLogFormatter.format(exception));
        }
    }

    MqttMessage createMessage(String payload) {
        MqttMessage message = new MqttMessage(
                payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(mqttProperties.getQos());
        message.setRetained(mqttProperties.isRetained());
        return message;
    }

    MqttClient createClient(String brokerUrl, String clientId)
            throws MqttException {
        return new MqttClient(
                brokerUrl, clientId, new MemoryPersistence());
    }
}
