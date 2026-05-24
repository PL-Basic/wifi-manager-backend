package com.plagod.dto;

import lombok.Data;

import java.util.List;

@Data
public class TrafficEvaluationResult {
    private boolean hit;
    private List<RuleHitVO> hits;
    private Long alertId;
}
