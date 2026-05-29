package com.plagod.service;


import com.plagod.dto.*;

public interface UserService {

    //注册业务
    public RegisterResult register(RegisterDTO registerDTO,String verifyIp);

    public LoginResult login(LoginDTO loginDTO);

    public LoginResult loginByVerifyCode(LoginByVerifyCodeDTO loginByVerifyCodeDTO, String verifyIp);
}
