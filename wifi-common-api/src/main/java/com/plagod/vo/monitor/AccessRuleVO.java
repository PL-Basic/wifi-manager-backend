package com.plagod.vo.monitor;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AccessRuleVO {
    private Long id;
    private String ruleCode;
    private Integer ruleType;
    private String pattern;
    private Integer actionType;
    private Integer level;
    private Integer enabled;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
