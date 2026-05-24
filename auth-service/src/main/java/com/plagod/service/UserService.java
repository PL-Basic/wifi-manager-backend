package com.plagod.service;


import com.plagod.dto.*;

public interface UserService {

    //注册业务
    public RegisterResult register(RegisterDTO registerDTO);

    public LoginResult login(LoginDTO loginDTO);
}
