package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.AuthUserDTO;
import com.plagod.dto.UserRegisterCommandDTO;
import com.plagod.dto.UserRegisterResultDTO;
import com.plagod.service.UserAuthInternalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth/users")
public class UserAuthInternalController {

    @Autowired
    private UserAuthInternalService userAuthInternalService;

    @GetMapping("/lookup")
    public ApiResponse<AuthUserDTO> findByAccount(@RequestParam String account) {
        return ApiResponse.success(userAuthInternalService.findByAccount(account));
    }

    @PostMapping("/register")
    public ApiResponse<UserRegisterResultDTO> register(@RequestBody UserRegisterCommandDTO command) {
        return ApiResponse.success(userAuthInternalService.register(command));
    }
}
