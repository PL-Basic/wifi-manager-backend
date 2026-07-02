package com.plagod.dto.monitor;

import lombok.Data;

import javax.validation.constraints.Size;

@Data
public class AccessRuleUpdateDTO {

    private Integer ruleType;

    @Size(max = 255, message = "pattern 长度不能超过 255")
    private String pattern;

    private Integer actionType;

    private Integer level;

    private Integer enabled;

    @Size(max = 255, message = "描述长度不能超过 255")
    private String description;
}
