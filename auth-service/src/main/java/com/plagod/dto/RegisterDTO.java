package com.plagod.dto;


import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class RegisterDTO{
    //不能随便加属性列，使用了直接拷贝
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度须在3-20之间")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需要在6-20之间")
    private String password;
    @NotBlank(message = "昵称不要忘记填~")
    private String nickname;
    @Email(message = "邮箱的格式不对哦~")
    private String email;
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    @Pattern(regexp = "^[A-Za-z0-9]{6}$",message = "邮箱验证码格式不正确")
    private String emailCode;
    @Pattern(regexp = "^[A-Za-z0-9]{6}$",message = "手机验证码格式不正确")
    private String phoneCode;
}
