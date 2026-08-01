package com.plagod.dto.user;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class UserStatusDTO {

    @Min(value = 0, message = "用户状态只能是0或1")
    @Max(value = 1, message = "用户状态只能是0或1")
    @NotNull(message = "用户状态不能为空")
    private Integer status;
}
