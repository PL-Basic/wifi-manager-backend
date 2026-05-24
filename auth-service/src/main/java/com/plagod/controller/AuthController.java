package com.plagod.controller;


import com.plagod.dto.*;
import com.plagod.enums.LoginStatusEnum;
import com.plagod.enums.RegisterStatusEnum;
import com.plagod.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ApiResponse<RegisterResult> register(@Valid @RequestBody RegisterDTO registerDTO) {
        RegisterResult registerResult = userService.register(registerDTO);
        if (registerResult.getStatus() == RegisterStatusEnum.SUCCESS) {
            return ApiResponse.success(registerResult.getMessage(), registerResult);
        }else{
            return ApiResponse.fail(400, registerResult.getMessage(), registerResult);
        }
    }

    @PostMapping("/login")
    public ApiResponse<AuthResultDTO> login(@Valid @RequestBody LoginDTO loginDTO) {
        LoginResult loginResult = userService.login(loginDTO);
        if (loginResult.getStatus() == LoginStatusEnum.SUCCESS) {
            return ApiResponse.success(loginResult.getMessage(), loginResult.getData());
        }else {
            return ApiResponse.fail(400,loginResult.getMessage(),loginResult.getData());
        }

    }





}
