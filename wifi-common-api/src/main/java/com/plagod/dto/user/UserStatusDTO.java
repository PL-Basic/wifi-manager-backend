package com.plagod.dto.user;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class UserStatusDTO {
    @NotNull(message = "用户状态不能为空")
    private Integer status;
}
