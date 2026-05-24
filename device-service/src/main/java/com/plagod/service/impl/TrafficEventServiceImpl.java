package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.client.MonitorServiceClient;
import com.plagod.constant.MqttTopics;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.DeviceTrafficEvent;
import com.plagod.dto.RuleHitVO;
import com.plagod.dto.TrafficEvaluationRequest;
import com.plagod.dto.TrafficEvaluationResult;
import com.plagod.entity.Esp32Node;
import com.plagod.entity.SessionRecord;
import com.plagod.entity.TrafficLog;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.mapper.TrafficLogMapper;
import com.plagod.mqtt.MqttCommandPublisher;
import com.plagod.service.TrafficEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrafficEventServiceImpl implements TrafficEventService {

    private static final Logger log = LoggerFactory.getLogger(TrafficEventServiceImpl.class);

    private static final int ACTION_KICK = 1;
    private static final int ACTION_BLOCK_TRAFFIC = 2;
    private static final int ACTION_ALERT_ONLY = 3;

    @Autowired
    private TrafficLogMapper trafficLogMapper;

    @Autowired
    private SessionRecordMapper sessionRecordMapper;

    @Autowired
    private Esp32NodeMapper esp32NodeMapper;

    @Autowired
    private MonitorServiceClient monitorServiceClient;

    @Autowired
    private MqttCommandPublisher mqttCommandPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handleTrafficEvent(DeviceTrafficEvent event) {
        if (event == null || !StringUtils.hasText(event.getMac())) {
            throw new IllegalArgumentException("流量事件缺少 mac");
        }

        Long sessionId = event.getSessionId();
        if (sessionId == null) {
            QueryWrapper<SessionRecord> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("mac", event.getMac())
                    .eq("status", 1)
                    .orderByDesc("login_time")
                    .last("limit 1");
            SessionRecord sessionRecord = sessionRecordMapper.selectOne(queryWrapper);
            if (sessionRecord != null) {
                sessionId = sessionRecord.getSessionId();
            }
        }

        if (sessionId == null) {
            throw new IllegalArgumentException("未找到可关联的在线会话");
        }

        TrafficLog trafficLog = new TrafficLog();
        trafficLog.setSessionId(sessionId);
        trafficLog.setMac(event.getMac());
        trafficLog.setDstIp(event.getDstIp());
        trafficLog.setDstPort(event.getDstPort());
        trafficLog.setSni(event.getSni());
        trafficLog.setProtocol(event.getProtocol());
        trafficLog.setBytesUp(event.getBytesUp() == null ? 0L : event.getBytesUp());
        trafficLog.setBytesDown(event.getBytesDown() == null ? 0L : event.getBytesDown());
        trafficLogMapper.insert(trafficLog);

        QueryWrapper<SessionRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("session_id", sessionId);
        SessionRecord sessionRecord = sessionRecordMapper.selectOne(queryWrapper);
        if (sessionRecord != null) {
            sessionRecord.setBytesUp((sessionRecord.getBytesUp() == null ? 0L : sessionRecord.getBytesUp()) + trafficLog.getBytesUp());
            sessionRecord.setBytesDown((sessionRecord.getBytesDown() == null ? 0L : sessionRecord.getBytesDown()) + trafficLog.getBytesDown());
            sessionRecordMapper.updateById(sessionRecord);
        }

        TrafficEvaluationResult result = evaluateAgainstRules(event, sessionId, sessionRecord);
        if (result != null && result.isHit()) {
            executeActions(event, sessionRecord, result);
        }
    }

    private TrafficEvaluationResult evaluateAgainstRules(DeviceTrafficEvent event, Long sessionId, SessionRecord sessionRecord) {
        TrafficEvaluationRequest request = new TrafficEvaluationRequest();
        request.setMac(event.getMac());
        request.setSessionId(sessionId);
        request.setUserId(sessionRecord == null ? null : sessionRecord.getUserId());
        request.setDstIp(event.getDstIp());
        request.setDstPort(event.getDstPort());
        request.setSni(event.getSni());
        request.setProtocol(event.getProtocol());

        try {
            ApiResponse<TrafficEvaluationResult> response = monitorServiceClient.evaluate(request);
            if (response == null || response.getData() == null) {
                return null;
            }
            TrafficEvaluationResult result = response.getData();
            if (result.isHit()) {
                log.info("traffic event {} matched {} rule(s), alertId={}",
                        event.getMac(), result.getHits() == null ? 0 : result.getHits().size(), result.getAlertId());
            }
            return result;
        } catch (Exception ex) {
            log.warn("monitor evaluate failed for mac={} dstIp={}: {}", event.getMac(), event.getDstIp(), ex.getMessage());
            return null;
        }
    }

    private void executeActions(DeviceTrafficEvent event, SessionRecord sessionRecord, TrafficEvaluationResult result) {
        Integer action = pickStrongestAction(result.getHits());
        if (action == null || action == ACTION_ALERT_ONLY) {
            return;
        }

        String deviceCode = resolveDeviceCode(event, sessionRecord);
        if (deviceCode == null) {
            log.warn("auto-action skipped, deviceCode unknown for mac={} sessionId={} alertId={}",
                    event.getMac(), sessionRecord == null ? null : sessionRecord.getSessionId(), result.getAlertId());
            return;
        }

        try {
            if (action == ACTION_KICK) {
                publishDisconnectMac(deviceCode, event.getMac(), result.getAlertId());
            } else if (action == ACTION_BLOCK_TRAFFIC) {
                publishBlockTraffic(deviceCode, event.getDstIp(), event.getSni(), result.getAlertId());
            }
        } catch (Exception ex) {
            log.warn("auto-action publish failed action={} mac={} alertId={}: {}",
                    action, event.getMac(), result.getAlertId(), ex.getMessage());
        }
    }

    private Integer pickStrongestAction(List<RuleHitVO> hits) {
        if (hits == null || hits.isEmpty()) {
            return null;
        }
        Integer best = null;
        for (RuleHitVO hit : hits) {
            Integer a = hit.getActionType();
            if (a == null) continue;
            if (best == null || a < best) {
                best = a;
            }
        }
        return best;
    }

    private String resolveDeviceCode(DeviceTrafficEvent event, SessionRecord sessionRecord) {
        if (StringUtils.hasText(event.getDeviceCode())) {
            return event.getDeviceCode();
        }
        if (sessionRecord == null || sessionRecord.getNodeId() == null) {
            return null;
        }
        Esp32Node node = esp32NodeMapper.selectById(sessionRecord.getNodeId());
        return node == null ? null : node.getDeviceCode();
    }

    private void publishDisconnectMac(String deviceCode, String mac, Long alertId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mac", mac);
        body.put("alertId", alertId);
        String topic = MqttTopics.deviceDisconnectMac(deviceCode);
        String payload = toJson(body);
        mqttCommandPublisher.publish(topic, payload);
        log.info("auto-action disconnect-mac deviceCode={} mac={} alertId={}", deviceCode, mac, alertId);
    }

    private void publishBlockTraffic(String deviceCode, String dstIp, String sni, Long alertId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dstIp", dstIp);
        body.put("sni", sni);
        body.put("alertId", alertId);
        String topic = MqttTopics.deviceBlockTraffic(deviceCode);
        String payload = toJson(body);
        mqttCommandPublisher.publish(topic, payload);
        log.info("auto-action block-traffic deviceCode={} dstIp={} sni={} alertId={}", deviceCode, dstIp, sni, alertId);
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("MQTT payload 序列化失败", ex);
        }
    }
}
