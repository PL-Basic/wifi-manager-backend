package com.plagod.service;

import com.plagod.client.MonitorServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.DeviceTrafficEvent;
import com.plagod.dto.RuleHitVO;
import com.plagod.dto.TrafficEvaluationRequest;
import com.plagod.dto.TrafficEvaluationResult;
import com.plagod.entity.Esp32Node;
import com.plagod.entity.SessionRecord;
import com.plagod.mapper.Esp32NodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 异步评估 traffic + 派发自动 action。从 TrafficEventServiceImpl 抽出来有两个目的：
 *
 *  1. @Async 需要走 spring 代理，否则同 bean 的 this 调用绕过代理，注解形同虚设。
 *  2. 落库（t_traffic_log）是 MQTT 接收线程的同步关键路径，必须保留；评估 + 动作
 *     是可以丢线程池的旁路，被 monitor-service 卡住时不能反压到 MQTT 消费。
 */
@Service
public class TrafficRuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(TrafficRuleEvaluator.class);

    private static final int ACTION_KICK = 1;
    private static final int ACTION_BLOCK_TRAFFIC = 2;
    private static final int ACTION_ALERT_ONLY = 3;

    @Autowired
    private Esp32NodeMapper esp32NodeMapper;

    @Autowired
    private MonitorServiceClient monitorServiceClient;

    @Autowired
    private RuleActionExecutor ruleActionExecutor;

    @Async("monitorEvalExecutor")
    public void evaluateAndAct(DeviceTrafficEvent event, Long sessionId, SessionRecord sessionRecord) {
        TrafficEvaluationResult result = callEvaluate(event, sessionId, sessionRecord);
        if (result != null && result.isHit()) {
            executeActions(event, sessionRecord, result);
        }
    }

    private TrafficEvaluationResult callEvaluate(DeviceTrafficEvent event, Long sessionId, SessionRecord sessionRecord) {
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
                ruleActionExecutor.disconnectMac(deviceCode, event.getMac(), result.getAlertId());
            } else if (action == ACTION_BLOCK_TRAFFIC) {
                ruleActionExecutor.blockTraffic(deviceCode, event.getDstIp(), event.getSni(), result.getAlertId());
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
}
