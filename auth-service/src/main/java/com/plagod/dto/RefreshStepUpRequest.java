package com.plagod.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class RefreshStepUpRequest {

    @NotBlank(message = "验证码目标不能为空")
    @Size(max = 320, message = "验证码目标长度无效")
    private String target;

    @NotBlank(message = "验证码不能为空")
    @Size(max = 16, message = "验证码长度无效")
    private String code;
}
