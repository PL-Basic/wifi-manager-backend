package com.plagod.dto.tenant;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class TenantCreateRequest {

    @NotBlank(message = "租户编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9-]{2,63}$", message = "租户编码必须以小写字母开头，且只能包含小写字母、数字和短横线")
    private String tenantCode;

    @NotBlank(message = "租户名称不能为空")
    @Size(max = 128, message = "租户名称不能超过128个字符")
    private String name;

    @NotBlank(message = "时区不能为空")
    @Size(max = 64, message = "时区不能超过64个字符")
    private String timezone;

}
