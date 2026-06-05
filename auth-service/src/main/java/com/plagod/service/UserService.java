package com.plagod.service;


import com.plagod.dto.*;

public interface UserService {

    //注册业务
    public RegisterResult register(RegisterDTO registerDTO,String verifyIp);

    //登录业务
    public LoginResult login(LoginDTO loginDTO);

    //验证码登录业务
    public LoginResult loginByVerifyCode(LoginByVerifyCodeDTO loginByVerifyCodeDTO, String verifyIp);

    //重置密码业务
    public void resetPassword(ResetPasswordDTO resetPasswordDTO,String verifyIp);

}
