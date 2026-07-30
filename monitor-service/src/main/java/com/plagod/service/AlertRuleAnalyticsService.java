package com.plagod.service;

import com.plagod.dto.AlertRuleAnalyticsQueryCriteria;
import com.plagod.mapper.AlertEventMapper;
import com.plagod.mapper.RuleHitRecordMapper;
import com.plagod.vo.monitor.AlertRuleAnalyticsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AlertRuleAnalyticsService {

    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");

    @Autowired
    private AlertEventMapper alertEventMapper;

    @Autowired
    private RuleHitRecordMapper ruleHitRecordMapper;

    @Transactional(readOnly = true)
    public AlertRuleAnalyticsVO query(Long userId, String mac, Long sessionId, Long nodeId, String deviceCode, LocalDateTime startTime, LocalDateTime endTime, Integer topLimit) {

        validate(userId, mac, sessionId, nodeId, deviceCode, startTime, endTime, topLimit);

        AlertRuleAnalyticsQueryCriteria criteria = new AlertRuleAnalyticsQueryCriteria();

        criteria.setUserId(userId);
        criteria.setMac(normalizeMac(mac));
        criteria.setSessionId(sessionId);
        criteria.setNodeId(nodeId);
        criteria.setDeviceCode(cleanDeviceCode(deviceCode));
        criteria.setStartTime(startTime);
        criteria.setEndTime(endTime);
        criteria.setTopLimit(topLimit);

        AlertRuleAnalyticsVO result = new AlertRuleAnalyticsVO();
        result.setUserId(userId);
        result.setMac(criteria.getMac());
        result.setSessionId(sessionId);
        result.setNodeId(nodeId);
        result.setDeviceCode(criteria.getDeviceCode());
        result.setStartTime(startTime);
        result.setEndTime(endTime);
        result.setTopLimit(topLimit);

        result.setAlertSummary(alertEventMapper.selectAnalyticsSummary(criteria));
        result.setAlertLevels(alertEventMapper.selectLevelDistribution(criteria));
        result.setAlertStatuses(alertEventMapper.selectStatusDistribution(criteria));

        result.setRuleHitSummary(ruleHitRecordMapper.selectAnalyticsSummary(criteria));
        result.setRules(ruleHitRecordMapper.selectRuleRanking(criteria));
        result.setActionTypes(ruleHitRecordMapper.selectActionDistribution(criteria));

        return result;
    }

    private void validate(Long userId, String mac, Long sessionId, Long nodeId, String deviceCode, LocalDateTime startTime, LocalDateTime endTime, Integer topLimit) {

        requirePositive(userId, "userId");
        requirePositive(sessionId, "sessionId");
        requirePositive(nodeId, "nodeId");

        if (StringUtils.hasText(mac) && !MAC_PATTERN.matcher(mac.trim()).matches()) {
            throw new IllegalArgumentException("MAC格式不正确");
        }

        if (StringUtils.hasText(deviceCode) && deviceCode.trim().length() > 64) {
            throw new IllegalArgumentException("deviceCode长度不能超过64");
        }

        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("时间范围无效");
        }

        if (Duration.between(startTime, endTime)
                .compareTo(Duration.ofDays(31)) > 0) {
            throw new IllegalArgumentException("统计时间范围不能超过31天");
        }

        if (topLimit == null || topLimit < 1 || topLimit > 50) {
            throw new IllegalArgumentException("topLimit必须在1到50之间");
        }
    }

    private void requirePositive(Long value, String field) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(field + "无效");
        }
    }

    private String normalizeMac(String mac) {
        return StringUtils.hasText(mac) ? mac.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String cleanDeviceCode(String deviceCode) {
        return StringUtils.hasText(deviceCode) ? deviceCode.trim() : null;
    }
}