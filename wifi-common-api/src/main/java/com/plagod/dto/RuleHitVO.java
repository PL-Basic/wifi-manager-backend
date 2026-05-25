package com.plagod.dto;

import lombok.Data;

@Data
public class RuleHitVO {
    private Long ruleId;
    private String ruleCode;
    private Integer ruleType;
    private String pattern;
    private Integer actionType;
    private Integer level;
    private String description;
}
