package com.plagod.vo.monitor;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AlertRuleAnalyticsVO {

    private Long userId;
    private String mac;
    private Long sessionId;
    private Long nodeId;
    private String deviceCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer topLimit;

    private AlertSummary alertSummary;
    private RuleHitSummary ruleHitSummary;
    private List<CountBucket> alertLevels;
    private List<CountBucket> alertStatuses;
    private List<RuleBucket> rules;
    private List<CountBucket> actionTypes;

    @Data
    public static class AlertSummary {
        private Long totalAlerts;
        private Long unresolvedAlerts;
        private Long handledAlerts;
    }

    @Data
    public static class RuleHitSummary {
        private Long totalHits;
        private Long actionableHits;
        private Long suppressedHits;
        private Long distinctEvents;
        private Long distinctRules;
    }

    @Data
    public static class CountBucket {
        private Integer code;
        private Long count;
    }

    @Data
    public static class RuleBucket {
        private String ruleCode;
        private Long hitCount;
        private Long actionableCount;
        private Long suppressedCount;
    }
}