package com.plagod.dto.user;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@Data
public class UserUpdateDTO {

    @Size(max = 64, message = "昵称不能超过64个字符")
    @Pattern(regexp = "^(?=.{1,64}$).*\\S.*$", message = "昵称不能为空白字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过128个字符")
    private String email;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 255, message = "头像地址不能超过255个字符")
    private String avatar;

    @Min(value = 0, message = "用户角色无效")
    @Max(value = 2, message = "用户角色无效")
    private Integer role;

    @Min(value = 1, message = "最大连接数必须大于0")
    private Integer maxConnections;

    @Min(value = 0, message = "每日配额不能为负数")
    private Integer dailyQuotaMinutes;

    private LocalDateTime expireTime;
}