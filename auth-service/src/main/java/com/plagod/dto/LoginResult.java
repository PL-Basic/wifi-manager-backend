package com.plagod.dto;

import com.plagod.enums.LoginStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
//处理登录返回的信息
public class LoginResult {
    private LoginStatusEnum status;
    private String message;
    private AuthResultDTO data;

    public static LoginResult success(AuthResultDTO data) {
        return new LoginResult(LoginStatusEnum.SUCCESS,"登录成功",data);
    }

    public static LoginResult fail(LoginStatusEnum status, String message) {
        return new LoginResult(status,message,null);
    }

}
