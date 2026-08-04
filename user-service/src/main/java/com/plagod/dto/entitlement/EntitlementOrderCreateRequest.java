package com.plagod.dto.entitlement;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

@Data
public class EntitlementOrderCreateRequest {

    @NotBlank(message = "客户端请求号不能为空")
    @Size(max = 64, message = "客户端请求号不能超过64个字符")
    private String clientRequestId;

    @NotBlank(message = "商品编码不能为空")
    @Size(max = 32, message = "商品编码不能超过32个字符")
    private String productCode;

    // 仅 DURATION_CUSTOM 使用，具体金额边界和时长换算由服务端商品配置决定。
    @Positive(message = "自定义金额必须大于0")
    private Long customAmountCents;
}
