package com.plagod.mqtt;

import com.plagod.configuration.MqttProperties;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MqttCommandPublisher {

    @Autowired
    private MqttProperties mqttProperties;

    public void publish(String topic, String payload) {
        try {
            String clientId = mqttProperties.getClientId() + "-publisher-" + System.currentTimeMillis();
            MqttClient client = new MqttClient(mqttProperties.getBrokerUrl(), clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            if (StringUtils.hasText(mqttProperties.getUsername())) {
                options.setUserName(mqttProperties.getUsername());
            }
            if (StringUtils.hasText(mqttProperties.getPassword())) {
                options.setPassword(mqttProperties.getPassword().toCharArray());
            }

            client.connect(options);
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(mqttProperties.getQos());
            client.publish(topic, message);
            client.disconnect();
            client.close();
        } catch (Exception e) {
            throw new IllegalStateException("MQTT 命令发布失败", e);
        }
    }
}
