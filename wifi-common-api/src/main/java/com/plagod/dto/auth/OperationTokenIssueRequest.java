package com.plagod.dto.auth;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class OperationTokenIssueRequest {

    @NotBlank(message = "操作目的不能为空")
    @Pattern(
            regexp = "^(CHANGE_PASSWORD|DELETE_ACCOUNT|PAYMENT|PRIVILEGE_CHANGE)$",
            message = "操作目的不受支持")
    private String purpose;

    @Size(max = 128, message = "密码长度无效")
    private String password;

    @Size(max = 320, message = "验证码目标长度无效")
    private String target;

    @Size(max = 16, message = "验证码长度无效")
    private String code;
}
