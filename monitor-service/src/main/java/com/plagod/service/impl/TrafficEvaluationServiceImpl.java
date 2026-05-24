package com.plagod.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.dto.RuleHitVO;
import com.plagod.dto.TrafficEvaluationRequest;
import com.plagod.dto.TrafficEvaluationResult;
import com.plagod.entity.AccessRule;
import com.plagod.entity.AlertEvent;
import com.plagod.mapper.AlertEventMapper;
import com.plagod.service.AccessRuleCache;
import com.plagod.service.TrafficEvaluationService;
import com.plagod.ws.AlertWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrafficEvaluationServiceImpl implements TrafficEvaluationService {

    @Autowired
    private AccessRuleCache accessRuleCache;

    @Autowired
    private AlertEventMapper alertEventMapper;

    @Autowired
    private AlertWebSocketHandler alertWebSocketHandler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public TrafficEvaluationResult evaluate(TrafficEvaluationRequest request) {
        TrafficEvaluationResult result = new TrafficEvaluationResult();
        result.setHit(false);
        result.setHits(Collections.emptyList());

        if (request == null) {
            return result;
        }

        List<RuleHitVO> hits = new ArrayList<>();
        for (AccessRule rule : accessRuleCache.getEnabledRules()) {
            if (matches(rule, request)) {
                hits.add(toHit(rule));
            }
        }
        if (hits.isEmpty()) {
            return result;
        }

        AlertEvent alert = buildAlert(request, hits);
        alertEventMapper.insert(alert);

        result.setHit(true);
        result.setHits(hits);
        result.setAlertId(alert.getId());

        broadcast(alert, hits);
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
}
