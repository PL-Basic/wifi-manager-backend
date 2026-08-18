package com.plagod.service;

import com.plagod.audit.AuditActorContext;
import com.plagod.client.MonitorServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.DeviceTrafficEvent;
import com.plagod.entity.device.TrafficLog;
import com.plagod.vo.RuleHitVO;
import com.plagod.dto.device.TrafficEvaluationRequest;
import com.plagod.vo.device.TrafficEvaluationResult;
import com.plagod.entity.device.Esp32Node;
import com.plagod.entity.device.SessionRecord;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.request.RequestId;
import com.plagod.security.TrustedRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

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
    private static final Pattern AUDIT_EVENT_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");

    @Autowired
    private Esp32NodeMapper esp32NodeMapper;

    @Autowired
    private MonitorServiceClient monitorServiceClient;

    @Autowired
    private RuleActionExecutor ruleActionExecutor;

    @Async("monitorEvalExecutor")
    public void evaluateAndAct(DeviceTrafficEvent event, TrafficLog trafficLog, SessionRecord sessionRecord) {
        Long tenantId = resolvePersistedTenantId(trafficLog, sessionRecord);
        if (tenantId == null) {
            log.warn("monitor evaluate skipped for inconsistent persisted traffic relation, eventId={}",
                    trafficLog == null ? null : trafficLog.getEventId());
            return;
        }

        TrafficEvaluationResult result = callEvaluate(event, trafficLog, sessionRecord, tenantId);

        if (result != null && result.isHit()) {
            executeActions(event, trafficLog, sessionRecord, result);
        }
    }

    private TrafficEvaluationResult callEvaluate(DeviceTrafficEvent event,
                                                 TrafficLog trafficLog,
                                                 SessionRecord sessionRecord,
                                                 Long tenantId) {

        TrafficEvaluationRequest request = new TrafficEvaluationRequest();

        request.setEventId(trafficLog.getEventId());
        request.setTenantId(tenantId);
        request.setDeviceCode(trafficLog.getDeviceCode());
        request.setNodeId(trafficLog.getNodeId());
        request.setSessionId(trafficLog.getSessionId());
        request.setUserId(sessionRecord == null ? null : sessionRecord.getUserId());
        request.setMac(trafficLog.getMac());
        request.setDstIp(trafficLog.getDstIp());
        request.setDstPort(trafficLog.getDstPort());
        request.setSni(trafficLog.getSni());
        request.setProtocol(trafficLog.getProtocol());
        request.setEventTime(trafficLog.getLogTime());

        try {
            ApiResponse<TrafficEvaluationResult> response =
                    monitorServiceClient.evaluate(request);

            if (response == null || response.getData() == null) {
                return null;
            }

            TrafficEvaluationResult result = response.getData();

            if (result.isHit()) {
                log.info("traffic event {} matched {} actionable rule(s), alertId={}", event.getEventId(), result.getHits() == null ? 0 : result.getHits().size(), result.getAlertId());
            }

            return result;
        } catch (Exception exception) {
            log.warn("monitor evaluate failed for eventId={}, type={}",
                    event.getEventId(), exception.getClass().getName());
            return null;
        }
    }

    private Long resolvePersistedTenantId(TrafficLog trafficLog,
                                          SessionRecord sessionRecord) {
        if (trafficLog == null || sessionRecord == null
                || trafficLog.getTenantId() == null
                || trafficLog.getTenantId() <= 0
                || !Objects.equals(
                        trafficLog.getTenantId(),
                        sessionRecord.getTenantId())
                || !Objects.equals(
                        trafficLog.getSessionId(),
                        sessionRecord.getSessionId())) {
            return null;
        }
        return trafficLog.getTenantId();
    }

    private void executeActions(DeviceTrafficEvent event,
                                TrafficLog trafficLog,
                                SessionRecord sessionRecord,
                                TrafficEvaluationResult result) {
        Integer action = pickStrongestAction(result.getHits());
        if (action == null || action == ACTION_ALERT_ONLY) {
            return;
        }

        String deviceCode = resolveDeviceCode(event, sessionRecord);
        if (deviceCode == null) {
            log.warn("auto-action skipped, deviceCode unknown for sessionId={} alertId={}",
                    sessionRecord == null ? null : sessionRecord.getSessionId(), result.getAlertId());
            return;
        }

        try {
            AuditActorContext actorContext = serviceActor(trafficLog);
            if (action == ACTION_KICK) {
                ruleActionExecutor.disconnectMac(
                        trafficLog.getTenantId(),
                        deviceCode,
                        event.getMac(),
                        result.getAlertId(),
                        actorContext);
            } else if (action == ACTION_BLOCK_TRAFFIC) {
                ruleActionExecutor.blockTraffic(
                        trafficLog.getTenantId(),
                        deviceCode,
                        event.getDstIp(),
                        event.getSni(),
                        result.getAlertId(),
                        actorContext);
            }
        } catch (Exception ex) {
            log.warn("auto-action publish failed action={} alertId={} type={}",
                    action, result.getAlertId(), ex.getClass().getName());
        }
    }

    private AuditActorContext serviceActor(TrafficLog trafficLog) {
        TrustedRequestContext context =
                TrustedRequestContext.scheduledService(
                        String.valueOf(trafficLog.getTenantId()),
                        RequestId.generate());
        String eventId = trafficLog.getEventId();
        String safeEventId = eventId != null
                && AUDIT_EVENT_ID_PATTERN.matcher(eventId).matches()
                ? eventId
                : null;
        return AuditActorContext.service(
                context,
                "device-service",
                safeEventId);
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
        Esp32Node node = esp32NodeMapper.selectByNodeIdAndTenantIncludeDeleted(
                sessionRecord.getTenantId(), sessionRecord.getNodeId());
        return node == null ? null : node.getDeviceCode();
    }
}
