package com.plagod.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class AccessRuleCreateDTO {

    @NotBlank(message = "规则编码不能为空")
    @Size(max = 64, message = "规则编码长度不能超过 64")
    private String ruleCode;

    @NotNull(message = "规则类型不能为空")
    private Integer ruleType;

    @NotBlank(message = "匹配 pattern 不能为空")
    @Size(max = 255, message = "pattern 长度不能超过 255")
    private String pattern;

    @NotNull(message = "动作类型不能为空")
    private Integer actionType;

    private Integer level;

    private Integer enabled;

    @Size(max = 255, message = "描述长度不能超过 255")
    private String description;
}
