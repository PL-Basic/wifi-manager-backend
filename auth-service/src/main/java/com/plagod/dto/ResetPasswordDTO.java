package com.plagod.dto;


import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class ResetPasswordDTO {

    @NotBlank(message = "邮箱或手机号不能为空")
    private String target;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9]{6}$",message = "验证码格式不正确")
    private String code;
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度需要在6-20之间")
    private String newPassword;
}
