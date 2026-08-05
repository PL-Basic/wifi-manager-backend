package com.plagod.dto.auth;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class OperationTokenConsumeRequest {

    @NotBlank(message = "一次性操作凭证不能为空")
    private String token;

    @NotBlank(message = "操作目的不能为空")
    private String purpose;

    @NotBlank(message = "业务幂等键不能为空")
    @Size(max = 128, message = "业务幂等键不能超过128个字符")
    private String businessKey;
}
