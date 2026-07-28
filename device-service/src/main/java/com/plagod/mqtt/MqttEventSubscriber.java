package com.plagod.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.configuration.MqttProperties;
import com.plagod.dto.*;
import com.plagod.service.*;
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

import java.nio.charset.StandardCharsets;

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

    @Autowired
    private ClientSignalEventService clientSignalEventService;

    @Autowired
    private CommandResultEventService commandResultEventService;

    @Autowired
    private ClientDisconnectEventService clientDisconnectEventService;

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
        log.info("MQTT 设备状态订阅已启动，topic={}", mqttProperties.getStatusTopic());

        client.subscribe(mqttProperties.getCommandResultTopic(), mqttProperties.getQos());

        client.subscribe(mqttProperties.getTrafficTopic(), mqttProperties.getQos());
        log.info("MQTT 设备流量订阅已启动，topic={}", mqttProperties.getTrafficTopic());

        client.subscribe(mqttProperties.getClientSignalTopic(), mqttProperties.getQos());
        log.info("MQTT 客户端 RSSI 订阅已启动，topic={}", mqttProperties.getClientSignalTopic());

        client.subscribe(mqttProperties.getClientDisconnectTopic(), mqttProperties.getQos());
        log.info("MQTT 客户端断线订阅已启动，topic={}", mqttProperties.getClientDisconnectTopic());


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
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
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
                return;
            }
            if (topic.endsWith("/event/client-signal")) {

                ClientSignalEvent event = objectMapper.readValue(payload, ClientSignalEvent.class);

                // MQTT topic 是设备身份的可信来源。
                String topicDeviceCode = parseDeviceCode(topic);
                if (!StringUtils.hasText(topicDeviceCode)) {
                    throw new IllegalArgumentException("无法从 MQTT topic 中解析 deviceCode");
                }

                // payload 携带 deviceCode 时，必须与 topic 保持一致。
                if (StringUtils.hasText(event.getDeviceCode()) && !topicDeviceCode.equals(event.getDeviceCode().trim())) {
                    throw new IllegalArgumentException("MQTT topic 与 payload 的 deviceCode 不一致");
                }

                // 统一使用 topic 中解析出的设备编码。
                event.setDeviceCode(topicDeviceCode);
                clientSignalEventService.handleClientSignalEvent(event);
                log.info("客户端 RSSI 事件处理成功，topic={}, clientCount={}", topic, event.getClients() == null ? 0 : event.getClients().size());
                return;
            }
            if (topic.endsWith("/event/command-result")) {
                CommandResultEvent event = objectMapper.readValue(payload, CommandResultEvent.class);

                // topic 是设备身份的主要来源。
                String topicDeviceCode = parseDeviceCode(topic);

                if (!StringUtils.hasText(topicDeviceCode)) {
                    throw new IllegalArgumentException("无法从 command-result topic 解析 deviceCode");
                }

                // payload 如果提供 deviceCode，必须与 topic 完全一致。
                if (StringUtils.hasText(event.getDeviceCode()) && !topicDeviceCode.equals(event.getDeviceCode().trim())) {
                    throw new IllegalArgumentException("command-result topic 与 payload 的 deviceCode 不一致");
                }

                event.setDeviceCode(topicDeviceCode);
                commandResultEventService.handleCommandResult(event);

                log.info("命令执行结果处理完成，topic={}, requestId={}, success={}", topic, event.getRequestId(), event.getSuccess());
                return;
            }
            if (topic.endsWith("/event/client-disconnect")) {
                ClientDisconnectEvent event = objectMapper.readValue(payload, ClientDisconnectEvent.class);

                // topic 才是设备身份的可信来源。
                String topicDeviceCode = parseDeviceCode(topic);
                if (!StringUtils.hasText(topicDeviceCode)) {
                    throw new IllegalArgumentException("无法从 client-disconnect topic 解析 deviceCode");
                }

                // payload 提供 deviceCode 时必须和 topic 一致。
                if (StringUtils.hasText(event.getDeviceCode()) && !topicDeviceCode.equals(event.getDeviceCode().trim())) {
                    throw new IllegalArgumentException("client-disconnect topic 与 payload 的 deviceCode 不一致");
                }

                event.setDeviceCode(topicDeviceCode);
                clientDisconnectEventService.handleClientDisconnectEvent(event);

                log.info("客户端断线事件处理完成，topic={}, mac={}, sessionId={}", topic, event.getMac(), event.getSessionId());
                return;
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