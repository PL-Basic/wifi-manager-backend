package com.plagod.dto.tenant;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class TenantStatusRequest {

    @NotBlank(message = "租户状态不能为空")
    @Pattern(regexp = "^(ACTIVE|DISABLED)$", message = "租户状态只能是ACTIVE或DISABLED")
    private String status;
}
