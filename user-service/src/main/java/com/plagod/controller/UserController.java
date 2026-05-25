package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.UserPageResult;
import com.plagod.dto.UserStatsVO;
import com.plagod.dto.UserStatusDTO;
import com.plagod.dto.UserUpdateDTO;
import com.plagod.dto.UserVO;
import com.plagod.service.UserManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserManageService userManageService;

    @GetMapping
    public ApiResponse<UserPageResult> pageUsers(@RequestParam(defaultValue = "1") Long current,
                                                 @RequestParam(defaultValue = "10") Long size,
                                                 @RequestParam(required = false) String keyword) {
        return ApiResponse.success(userManageService.pageUsers(current, size, keyword));
    }

    @GetMapping("/stats")
    public ApiResponse<UserStatsVO> getUserStats() {
        return ApiResponse.success(userManageService.getUserStats());
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserVO> getUser(@PathVariable Long userId) {
        return ApiResponse.success(userManageService.getUser(userId));
    }

    @PutMapping("/{userId}")
    public ApiResponse<UserVO> updateUser(@PathVariable Long userId,
                                          @RequestHeader(value = "X-User-Id", required = false) Long currentUserId,
                                          @RequestHeader(value = "X-User-Role", required = false) Integer currentRole,
                                          @RequestBody UserUpdateDTO updateDTO) {
        if (!Integer.valueOf(1).equals(currentRole) && userId.equals(currentUserId)) {
            updateDTO.setRole(null);
            updateDTO.setMaxConnections(null);
            updateDTO.setDailyQuotaMinutes(null);
            updateDTO.setExpireTime(null);
        }
        return ApiResponse.success("用户信息修改成功", userManageService.updateUser(userId, updateDTO));
    }

    @PutMapping("/{userId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long userId,
                                          @Valid @RequestBody UserStatusDTO statusDTO) {
        userManageService.updateStatus(userId, statusDTO);
        return ApiResponse.success("用户状态修改成功", null);
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable Long userId) {
        userManageService.deleteUser(userId);
        return ApiResponse.success("用户已逻辑删除", null);
    }

    @DeleteMapping("/{userId}/purge")
    public ApiResponse<Void> purgeUser(@PathVariable Long userId) {
        userManageService.purgeUser(userId);
        return ApiResponse.success("用户已物理删除", null);
    }
}
