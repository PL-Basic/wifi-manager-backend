package com.plagod.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.audit.Audited;
import com.plagod.constant.MqttTopics;
import com.plagod.mqtt.MqttCommandPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 规则评估命中后的真实动作派发。独立成 @Service 是为了让 @Audited 切面能在
 * Spring 代理上生效 —— 如果留在 TrafficEventServiceImpl 内部用 private 方法，
 * 同一个 bean 内的 this 调用会绕过 AOP 代理，monitor-auto 类的审计就会丢。
 */
@Service
public class RuleActionExecutor {

    @Autowired
    private MqttCommandPublisher mqttCommandPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Audited(action = "monitor.auto.disconnect-mac", operatorName = "monitor-auto")
    public void disconnectMac(String deviceCode, String mac, Long alertId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mac", mac);
        body.put("alertId", alertId);
        mqttCommandPublisher.publish(MqttTopics.deviceDisconnectMac(deviceCode), toJson(body));
    }

    @Audited(action = "monitor.auto.block-traffic", operatorName = "monitor-auto")
    public void blockTraffic(String deviceCode, String dstIp, String sni, Long alertId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dstIp", dstIp);
        body.put("sni", sni);
        body.put("alertId", alertId);
        mqttCommandPublisher.publish(MqttTopics.deviceBlockTraffic(deviceCode), toJson(body));
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("MQTT payload 序列化失败", ex);
        }
    }
}
