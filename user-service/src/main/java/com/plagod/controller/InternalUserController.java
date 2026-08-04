package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.user.UserRoleBatchRequest;
import com.plagod.service.UserManageService;
import com.plagod.vo.user.UserConnectionPolicyVO;
import com.plagod.vo.user.UserRoleSnapshotVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {
    @Autowired
    private UserManageService userManageService;

    @GetMapping("/{userId}/connection-policy")
    public ApiResponse<UserConnectionPolicyVO> getConnectionPolicy(@PathVariable Long userId) {
        return ApiResponse.success(userManageService.getConnectionPolicy(userId));
    }

    @PostMapping("/role-snapshots")
    public ApiResponse<List<UserRoleSnapshotVO>> getRoleSnapshots(
            @Valid @RequestBody UserRoleBatchRequest request) {
        return ApiResponse.success(userManageService.getRoleSnapshots(request.getUserIds()));
    }
}
