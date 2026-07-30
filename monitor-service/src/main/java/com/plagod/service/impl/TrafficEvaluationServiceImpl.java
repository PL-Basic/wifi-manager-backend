package com.plagod.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.entity.monitor.RuleHitRecord;
import com.plagod.mapper.RuleHitRecordMapper;
import com.plagod.vo.RuleHitVO;
import com.plagod.dto.device.TrafficEvaluationRequest;
import com.plagod.vo.device.TrafficEvaluationResult;
import com.plagod.entity.monitor.AccessRule;
import com.plagod.entity.monitor.AlertEvent;
import com.plagod.mapper.AlertEventMapper;
import com.plagod.service.AccessRuleCache;
import com.plagod.service.TrafficEvaluationService;
import com.plagod.ws.AlertWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TrafficEvaluationServiceImpl implements TrafficEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(TrafficEvaluationServiceImpl.class);

    @Autowired
    private AccessRuleCache accessRuleCache;

    @Autowired
    private AlertEventMapper alertEventMapper;

    @Autowired
    private AlertWebSocketHandler alertWebSocketHandler;

    @Autowired
    private RuleHitRecordMapper ruleHitRecordMapper;

    @Value("${monitor.evaluation.cooldown-seconds:30}")
    private long cooldownSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** (mac|ruleCode) -> last trigger epoch millis. 同一 mac 命中同一规则在冷却内不再触发告警/动作。 */
    private final ConcurrentHashMap<String, Long> lastHit = new ConcurrentHashMap<>();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrafficEvaluationResult evaluate(TrafficEvaluationRequest request) {

        TrafficEvaluationResult result = new TrafficEvaluationResult();
        result.setHit(false);
        result.setHits(Collections.emptyList());

        validateEventIdentity(request);

        long now = System.currentTimeMillis();
        long cooldownMillis = cooldownSeconds * 1000L;

        List<RuleHitVO> actionableHits = new ArrayList<>();
        Map<String, Long> cooldownReservations = new LinkedHashMap<>();
        int suppressedCount = 0;

        // 数据库事务回滚时，撤销本次尚未真正生效的内存冷却状态。
        registerCooldownRollback(cooldownReservations);

        for (AccessRule rule : accessRuleCache.getEnabledRules()) {
            if (!matches(rule, request)) {
                continue;
            }

            String cooldownKey = request.getMac() + "|" + rule.getRuleCode();
            Long last = lastHit.get(cooldownKey);
            boolean suppressed = last != null && now - last < cooldownMillis;

            RuleHitRecord record = buildRuleHitRecord(request, rule, suppressed);

            // 同一设备、事件和规则只能处理一次。
            if (ruleHitRecordMapper.insertIgnore(record) != 1) {
                continue;
            }

            if (suppressed) {
                suppressedCount++;
                continue;
            }

            lastHit.put(cooldownKey, now);
            cooldownReservations.put(cooldownKey, now);
            actionableHits.add(toHit(rule));
        }

        if (suppressedCount > 0) {
            log.debug("evaluation persisted {} suppressed hit(s), eventId={}", suppressedCount, request.getEventId());
        }

        if (actionableHits.isEmpty()) {
            return result;
        }

        AlertEvent alert = buildAlert(request, actionableHits);
        alert.setCreateTime(LocalDateTime.now());

        if (alertEventMapper.insert(alert) != 1 || alert.getId() == null) {
            throw new IllegalStateException("规则告警保存失败");
        }

        int bound = ruleHitRecordMapper.bindAlert(request.getDeviceCode(), request.getEventId(), alert.getId());

        // 告警和所有可执行命中必须完整关联，否则整体回滚。
        if (bound != actionableHits.size()) {
            throw new IllegalStateException("规则命中与告警关联数量不一致");
        }

        result.setHit(true);
        result.setHits(actionableHits);
        result.setAlertId(alert.getId());

        // 只有数据库真正提交后才向管理员推送告警。
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCommit() {
                        broadcast(alert, actionableHits);
                    }
                }
        );

        return result;
    }

    private void broadcast(AlertEvent alert, List<RuleHitVO> hits) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "alert");
        payload.put("alertId", alert.getId());
        payload.put("level", alert.getLevel());
        payload.put("ruleCode", alert.getRuleCode());
        payload.put("title", alert.getTitle());
        payload.put("mac", alert.getMac());
        payload.put("userId", alert.getUserId());
        payload.put("createTime", alert.getCreateTime());
        payload.put("hits", hits);
        alertWebSocketHandler.broadcast(payload);
    }

    private boolean matches(AccessRule rule, TrafficEvaluationRequest req) {
        if (rule.getPattern() == null || rule.getRuleType() == null) {
            return false;
        }
        String pattern = rule.getPattern();
        String sni = req.getSni() == null ? "" : req.getSni().toLowerCase();
        String patternLower = pattern.toLowerCase();
        switch (rule.getRuleType()) {
            case 1:
                return req.getSni() != null && patternLower.equals(sni);
            case 2:
                return !sni.isEmpty() && sni.contains(patternLower);
            case 3:
                return pattern.equals(req.getDstIp());
            case 4:
                return !sni.isEmpty() && sni.contains(patternLower);
            default:
                return false;
        }
    }

    private RuleHitVO toHit(AccessRule rule) {
        RuleHitVO hit = new RuleHitVO();
        hit.setRuleId(rule.getId());
        hit.setRuleCode(rule.getRuleCode());
        hit.setRuleType(rule.getRuleType());
        hit.setPattern(rule.getPattern());
        hit.setActionType(rule.getActionType());
        hit.setLevel(rule.getLevel());
        hit.setDescription(rule.getDescription());
        return hit;
    }

    private AlertEvent buildAlert(TrafficEvaluationRequest req, List<RuleHitVO> hits) {
        RuleHitVO worst = hits.get(0);
        for (RuleHitVO hit : hits) {
            if (hit.getLevel() != null && (worst.getLevel() == null || hit.getLevel() < worst.getLevel())) {
                worst = hit;
            }
        }

        AlertEvent alert = new AlertEvent();
        alert.setLevel(worst.getLevel() == null ? 2 : worst.getLevel());
        alert.setRuleCode(worst.getRuleCode());
        alert.setTitle(buildTitle(worst, hits.size()));
        alert.setMac(req.getMac());
        alert.setUserId(req.getUserId());
        alert.setStatus(0);
        alert.setDetail(buildDetail(req, hits));
        return alert;
    }

    private String buildTitle(RuleHitVO worst, int hitCount) {
        String base = worst.getDescription();
        if (base == null || base.isEmpty()) {
            base = "命中规则 " + worst.getRuleCode();
        }
        return hitCount > 1 ? base + " 等 " + hitCount + " 条" : base;
    }

    private String buildDetail(TrafficEvaluationRequest req, List<RuleHitVO> hits) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("traffic", req);
        payload.put("hits", hits);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"serialize_failed\"}";
        }
    }

    private RuleHitRecord buildRuleHitRecord(TrafficEvaluationRequest request, AccessRule rule, boolean suppressed) {

        RuleHitRecord record = new RuleHitRecord();

        record.setEventId(request.getEventId());
        record.setDeviceCode(request.getDeviceCode());
        record.setNodeId(request.getNodeId());
        record.setSessionId(request.getSessionId());
        record.setUserId(request.getUserId());
        record.setMac(request.getMac());
        record.setRuleId(rule.getId());
        record.setRuleCode(rule.getRuleCode());
        record.setRuleType(rule.getRuleType());
        record.setActionType(rule.getActionType());
        record.setLevel(rule.getLevel() == null ? 2 : rule.getLevel());
        record.setSuppressed(suppressed ? 1 : 0);
        record.setHitTime(request.getEventTime());
        record.setCreateTime(LocalDateTime.now());

        return record;
    }

    private void validateEventIdentity(TrafficEvaluationRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("流量评估请求不能为空");
        }

        if (request.getEventId() == null || request.getEventId().trim().isEmpty() || request.getEventId().length() > 64) {
            throw new IllegalArgumentException("流量评估缺少有效eventId");
        }

        if (request.getDeviceCode() == null || request.getDeviceCode().trim().isEmpty() || request.getDeviceCode().length() > 64) {
            throw new IllegalArgumentException("流量评估缺少有效deviceCode");
        }

        if (request.getNodeId() == null || request.getNodeId() <= 0 || request.getSessionId() == null || request.getSessionId() <= 0) {
            throw new IllegalArgumentException("流量评估节点或Session无效");
        }

        if (request.getMac() == null || request.getMac().length() != 17 || request.getEventTime() == null) {
            throw new IllegalArgumentException("流量评估MAC或事件时间无效");
        }
    }

    private void registerCooldownRollback(Map<String, Long> cooldownReservations) {

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == TransactionSynchronization.STATUS_COMMITTED) {
                            return;
                        }

                        cooldownReservations.forEach((key, timestamp) -> lastHit.remove(key, timestamp));
                    }
                }
        );
    }
}