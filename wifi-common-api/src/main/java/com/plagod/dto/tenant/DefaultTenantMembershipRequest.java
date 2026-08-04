package com.plagod.dto.tenant;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class DefaultTenantMembershipRequest {

    @NotBlank(message = "事件ID不能为空")
    @Size(max = 64, message = "事件ID不能超过64个字符")
    private String eventId;

    @NotNull(message = "用户ID不能为空")
    @Min(value = 1, message = "用户ID必须大于0")
    private Long userId;

    @NotNull(message = "全局角色不能为空")
    @Min(value = 0, message = "全局角色无效")
    @Max(value = 2, message = "全局角色无效")
    private Integer role;
}
