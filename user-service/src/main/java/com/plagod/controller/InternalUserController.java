package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.UserManageService;
import com.plagod.vo.user.UserConnectionPolicyVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {
    @Autowired
    private UserManageService userManageService;

    @GetMapping("/{userId}/connection-policy")
    public ApiResponse<UserConnectionPolicyVO> getConnectionPolicy(@PathVariable Long userId) {
        return ApiResponse.success(userManageService.getConnectionPolicy(userId));
    }
}
