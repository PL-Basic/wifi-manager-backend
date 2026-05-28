package com.plagod.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class LoginByVerifyCodeDTO {
    @NotBlank(message = "手机号或者邮箱不能为空")
    private String target;
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9]{6}$", message = "验证码格式不正确")
    private String code;
}
