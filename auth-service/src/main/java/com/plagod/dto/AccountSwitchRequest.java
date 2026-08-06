package com.plagod.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class AccountSwitchRequest {

    @NotBlank(message = "验证码不能为空")
    private String code;

    @NotBlank(message = "目标账号ID不能为空")
    @Pattern(regexp = "^[1-9]\\d*$", message = "目标账号ID必须是大于0的整数")
    private String expectedUserId;

    @NotBlank(message = "验证渠道不能为空")
    @Pattern(regexp = "^(phone|email)$", message = "验证渠道不受支持")
    private String channel;
}
