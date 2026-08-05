package com.plagod.dto.tenant;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class PlatformTenantContextRequest {

    @NotBlank(message = "进入租户的原因不能为空")
    @Size(max = 255, message = "进入租户的原因不能超过255个字符")
    private String reason;
}
