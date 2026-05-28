package com.plagod.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class SendVerifyCodeDTO {
    @NotBlank(message = "接收方不能为空")
    private String target;
    @NotBlank(message = "验证场景不能为空")
    @Pattern(regexp = "^(register|login|reset_password|bind_contact)$",message = "验证场景不正确")
    private String scene;
}
