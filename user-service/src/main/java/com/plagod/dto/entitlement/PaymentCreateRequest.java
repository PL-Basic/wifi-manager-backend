package com.plagod.dto.entitlement;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class PaymentCreateRequest {

    @NotBlank(message = "支付请求号不能为空")
    @Size(max = 64, message = "支付请求号不能超过64个字符")
    private String requestId;

    @Size(max = 32, message = "支付渠道编码不能超过32个字符")
    private String channel;
}