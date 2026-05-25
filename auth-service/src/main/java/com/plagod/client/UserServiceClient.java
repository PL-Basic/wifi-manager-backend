package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.AuthUserDTO;
import com.plagod.dto.UserRegisterCommandDTO;
import com.plagod.dto.UserRegisterResultDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping("/internal/auth/users/lookup")
    ApiResponse<AuthUserDTO> findByAccount(@RequestParam("account") String account);

    @PostMapping("/internal/auth/users/register")
    ApiResponse<UserRegisterResultDTO> register(@RequestBody UserRegisterCommandDTO command);
}
