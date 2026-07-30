package com.plagod.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AlertRuleAnalyticsQueryCriteria {

    private Long userId;
    private String mac;
    private Long sessionId;
    private Long nodeId;
    private String deviceCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer topLimit;
}