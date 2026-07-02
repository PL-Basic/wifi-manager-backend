package com.plagod.controller;


import com.plagod.dto.*;
import com.plagod.dto.auth.LoginDTO;
import com.plagod.enums.LoginStatusEnum;
import com.plagod.enums.RegisterStatusEnum;
import com.plagod.service.UserService;
import com.plagod.utils.RequestIpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ApiResponse<RegisterResult> register(@Valid @RequestBody RegisterDTO registerDTO,
                                                HttpServletRequest request) {
        RegisterResult registerResult = userService.register(registerDTO,RequestIpUtils.getClientIP(request));
        if (registerResult.getStatus() == RegisterStatusEnum.SUCCESS) {
            return ApiResponse.success(registerResult.getMessage(), registerResult);
        }else{
            return ApiResponse.fail(400, registerResult.getMessage(), registerResult);
        }
    }

    @PostMapping("/login")
    public ApiResponse<?> login(@Valid @RequestBody LoginDTO loginDTO,
                                            HttpServletRequest request) {
        LoginResult loginResult = userService.login(loginDTO,RequestIpUtils.getClientIP(request));
        if (loginResult.getStatus() == LoginStatusEnum.SUCCESS) {
            return ApiResponse.success(loginResult.getMessage(), loginResult.getData());
        }else {
            return ApiResponse.fail(400,loginResult.getMessage(),loginResult);
        }

    }

    @PostMapping("/code-login")
    public ApiResponse<?> codeLogin(@Valid @RequestBody LoginByVerifyCodeDTO loginByVerifyCodeDTO,
                                                HttpServletRequest request) {
        LoginResult loginResult = userService.loginByVerifyCode(loginByVerifyCodeDTO, RequestIpUtils.getClientIP(request));
        if (loginResult.getStatus() == LoginStatusEnum.SUCCESS) {
            return ApiResponse.success(loginResult.getMessage(), loginResult.getData());
        }else {
            return ApiResponse.fail(400,loginResult.getMessage(),loginResult);
        }
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO resetPasswordDTO,
                                                       HttpServletRequest request) {
        try {
            userService.resetPassword(resetPasswordDTO,RequestIpUtils.getClientIP(request));
            return ApiResponse.success("重置密码成功",null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400,e.getMessage(),null);
        }
    }





}
