package com.plagod.dto.tenant;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class TenantUpdateRequest {

    @NotBlank(message = "租户名称不能为空")
    @Size(max = 128, message = "租户名称不能超过128个字符")
    private String name;

    @NotBlank(message = "时区不能为空")
    @Size(max = 64, message = "时区不能超过64个字符")
    private String timezone;
}
