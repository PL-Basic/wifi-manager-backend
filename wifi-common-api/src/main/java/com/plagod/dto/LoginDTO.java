package com.plagod.dto;


import lombok.Data;

import javax.validation.constraints.NotBlank;

//登录需要
@Data
public class LoginDTO {
    @NotBlank(message = "账号不能为空")
    private String account;
    @NotBlank(message = "密码不能为空")
    private String password;
}
