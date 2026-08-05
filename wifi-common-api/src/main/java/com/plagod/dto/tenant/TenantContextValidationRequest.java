package com.plagod.dto.tenant;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import java.util.List;

@Data
public class TenantContextValidationRequest {

    @NotBlank(message = "用户ID不能为空")
    @Pattern(regexp = "^[1-9]\\d*$", message = "用户ID必须是大于0的整数")
    private String userId;

    @NotNull(message = "全局角色不能为空")
    @Min(value = 0, message = "全局角色必须在0到2之间")
    @Max(value = 2, message = "全局角色必须在0到2之间")
    private Integer globalRole;

    @NotBlank(message = "上下文类型不能为空")
    @Pattern(regexp = "^(TENANT|PLATFORM|PLATFORM_TENANT)$", message = "上下文类型不受支持")
    private String contextType;

    @Pattern(regexp = "^[1-9]\\d*$", message = "租户ID必须是大于0的整数")
    private String tenantId;
    private String tenantCode;
    private String tenantRole;

    @Positive(message = "租户上下文版本必须大于0")
    private Long contextVersion;

    @Positive(message = "成员上下文版本必须大于0")
    private Long memberContextVersion;

    private List<String> authorities;

    @NotNull(message = "必须明确请求是否为写操作")
    private Boolean writeRequest;

    private Boolean legacyToken;
}
