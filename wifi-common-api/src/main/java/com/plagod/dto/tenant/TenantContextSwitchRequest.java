package com.plagod.dto.tenant;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class TenantContextSwitchRequest {

    @NotBlank(message = "租户ID不能为空")
    @Pattern(regexp = "^[1-9]\\d*$", message = "租户ID必须是大于0的整数")
    private String tenantId;
}
