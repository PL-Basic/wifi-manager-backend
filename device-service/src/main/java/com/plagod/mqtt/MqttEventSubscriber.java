package com.plagod.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.configuration.MqttProperties;
import com.plagod.dto.DeviceTrafficEvent;
import com.plagod.dto.DeviceStatusEvent;
import com.plagod.service.DeviceEventService;
import com.plagod.service.TrafficEventService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class MqttEventSubscriber implements InitializingBean, DisposableBean {

    @Autowired
    private MqttProperties mqttProperties;

    @Autowired
    private DeviceEventService deviceEventService;

    @Autowired
    private TrafficEventService trafficEventService;

    @Autowired
    private ObjectMapper objectMapper;

    private MqttClient client;

    @Override
    public void afterPropertiesSet() throws Exception {
        String clientId = mqttProperties.getClientId() + "-subscriber";
        client = new MqttClient(mqttProperties.getBrokerUrl(), clientId, new MemoryPersistence());
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT 订阅连接断开", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                handleMessage(topic, message);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });

        client.connect(buildOptions());
        client.subscribe(mqttProperties.getStatusTopic(), mqttProperties.getQos());
        client.subscribe(mqttProperties.getTrafficTopic(), mqttProperties.getQos());
        log.info("MQTT 设备状态订阅已启动，topic={}", mqttProperties.getStatusTopic());
        log.info("MQTT 设备流量订阅已启动，topic={}", mqttProperties.getTrafficTopic());
    }

    @Override
    public void destroy() throws Exception {
        if (client != null && client.isConnected()) {
            client.disconnect();
            client.close();
        }
    }

    private void handleMessage(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            if (topic.endsWith("/event/status")) {
                DeviceStatusEvent event = objectMapper.readValue(payload, DeviceStatusEvent.class);
                if (!StringUtils.hasText(event.getDeviceCode())) {
                    event.setDeviceCode(parseDeviceCode(topic));
                }
                deviceEventService.handleStatusEvent(event);
                log.info("设备状态事件处理成功，topic={}, payload={}", topic, payload);
                return;
            }
            if (topic.endsWith("/event/traffic")) {
                DeviceTrafficEvent event = objectMapper.readValue(payload, DeviceTrafficEvent.class);
                if (!StringUtils.hasText(event.getDeviceCode())) {
                    event.setDeviceCode(parseDeviceCode(topic));
                }
                trafficEventService.handleTrafficEvent(event);
                log.info("设备流量事件处理成功，topic={}, payload={}", topic, payload);
            }
        } catch (Exception e) {
            log.warn("MQTT 事件处理失败，topic={}", topic, e);
        }
    }

    private MqttConnectOptions buildOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        if (StringUtils.hasText(mqttProperties.getUsername())) {
            options.setUserName(mqttProperties.getUsername());
        }
        if (StringUtils.hasText(mqttProperties.getPassword())) {
            options.setPassword(mqttProperties.getPassword().toCharArray());
        }
        return options;
    }

    private String parseDeviceCode(String topic) {
        String[] parts = topic.split("/");
        if (parts.length >= 3) {
            return parts[2];
        }
        return null;
    }
}
