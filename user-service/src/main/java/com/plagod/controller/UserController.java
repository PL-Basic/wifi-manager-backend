package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.AvatarUploadResult;
import com.plagod.dto.user.UserPurgeRequestDTO;
import com.plagod.vo.user.UserPageResult;
import com.plagod.vo.user.UserOperationRequestPageResult;
import com.plagod.dto.user.UserOperationReviewDTO;
import com.plagod.vo.user.UserStatsVO;
import com.plagod.dto.user.UserStatusDTO;
import com.plagod.dto.user.UserUpdateDTO;
import com.plagod.vo.user.UserVO;
import com.plagod.service.AvatarStorageService;
import com.plagod.service.UserManageService;
import com.plagod.service.UserOperationRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserManageService userManageService;

    @Autowired
    private UserOperationRequestService userOperationRequestService;

    @Autowired
    private AvatarStorageService avatarStorageService;

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
        if (Integer.valueOf(2).equals(currentRole) && userId.equals(currentUserId)) {
            updateDTO.setRole(null);
            updateDTO.setMaxConnections(null);
            updateDTO.setDailyQuotaMinutes(null);
            updateDTO.setExpireTime(null);
        }
        if (isSelf(userId, currentUserId) && updateDTO.getRole() != null) {
            throw new IllegalArgumentException("不能修改自己的角色");
        }
        return ApiResponse.success("用户信息修改成功", userManageService.updateUser(userId, updateDTO, currentRole));
    }

    @PostMapping("/{userId}/avatar")
    public ApiResponse<AvatarUploadResult> uploadAvatar(@PathVariable Long userId,
                                                        @RequestHeader(value = "X-User-Id", required = false) Long currentUserId,
                                                        @RequestHeader(value = "X-User-Role", required = false) Integer currentRole,
                                                        @RequestParam("file") MultipartFile file) {
        if (Integer.valueOf(2).equals(currentRole) && !userId.equals(currentUserId)) {
            throw new IllegalArgumentException("普通用户只能上传自己的头像");
        }
        if (!Integer.valueOf(0).equals(currentRole) && !userId.equals(currentUserId)
                && userManageService.getUser(userId).getRole() <= 1) {
            throw new IllegalArgumentException("管理员之间不能互相修改");
        }
        AvatarUploadResult result = avatarStorageService.store(userId, file);
        UserUpdateDTO updateDTO = new UserUpdateDTO();
        updateDTO.setAvatar(result.getUrl());
        userManageService.updateUser(userId, updateDTO, currentRole);
        return ApiResponse.success("头像上传成功", result);
    }

    @PutMapping("/{userId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long userId,
                                          @RequestHeader(value = "X-User-Id", required = false) Long currentUserId,
                                          @RequestHeader(value = "X-User-Role", required = false) Integer currentRole,
                                          @Valid @RequestBody UserStatusDTO statusDTO) {
        if (isSelf(userId, currentUserId) && Integer.valueOf(0).equals(statusDTO.getStatus())) {
            throw new IllegalArgumentException("不能禁用自己的账号");
        }
        if (!Integer.valueOf(0).equals(currentRole) && userManageService.getUser(userId).getRole() <= 1) {
            throw new IllegalArgumentException("管理员之间不能互相修改");
        }
        userManageService.updateStatus(userId, statusDTO);
        return ApiResponse.success("用户状态修改成功", null);
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable Long userId,
                                        @RequestHeader(value = "X-User-Id", required = false) Long currentUserId,
                                        @RequestHeader(value = "X-User-Role", required = false) Integer currentRole) {
        if (isSelf(userId, currentUserId)) {
            throw new IllegalArgumentException("不能逻辑删除自己的账号");
        }
        if (!Integer.valueOf(0).equals(currentRole) && userManageService.getUser(userId).getRole() <= 1) {
            throw new IllegalArgumentException("管理员之间不能互相修改");
        }
        userManageService.deleteUser(userId);
        return ApiResponse.success("用户已逻辑删除", null);
    }

    @DeleteMapping("/{userId}/purge")
    public ApiResponse<Void> purgeUser(@PathVariable Long userId,
                                       @RequestHeader(value = "X-User-Id", required = false) Long currentUserId,
                                       @RequestHeader(value = "X-User-Role", required = false) Integer currentRole) {
        if (!Integer.valueOf(0).equals(currentRole)) {
            throw new IllegalArgumentException("只有超级管理员可以直接物理删除");
        }
        if (isSelf(userId, currentUserId)) {
            throw new IllegalArgumentException("不能物理删除自己的账号");
        }
        userManageService.purgeUser(userId);
        return ApiResponse.success("用户已物理删除", null);
    }

    @GetMapping("/operation-requests")
    public ApiResponse<UserOperationRequestPageResult> pageOperationRequests(@RequestParam(defaultValue = "1") Long current,
                                                                             @RequestParam(defaultValue = "10") Long size,
                                                                             @RequestParam(required = false) Integer status) {
        return ApiResponse.success(userOperationRequestService.pageRequests(current, size, status));
    }

    @PostMapping("/{userId}/purge-requests")
    public ApiResponse<Long> requestPurgeUser(@PathVariable Long userId,
                                              @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
                                              @RequestHeader(value = "X-User-Name", required = false) String requesterName,
                                              @RequestBody(required = false) UserPurgeRequestDTO userPurgeRequestDTO) {
        String reason = userPurgeRequestDTO == null ? null : userPurgeRequestDTO.getReason();
        Long requestId = userOperationRequestService.requestPurge(userId, requesterId, requesterName, reason);
        return ApiResponse.success("物理删除申请已提交", requestId);
    }

    @PutMapping("/operation-requests/{id}/review")
    public ApiResponse<Void> reviewOperationRequest(@PathVariable Long id,
                                                    @RequestHeader(value = "X-User-Id", required = false) Long approverId,
                                                    @RequestHeader(value = "X-User-Name", required = false) String approverName,
                                                    @RequestBody UserOperationReviewDTO dto) {
        userOperationRequestService.review(id, approverId, approverName, dto);
        return ApiResponse.success("审批完成", null);
    }

    private boolean isSelf(Long userId, Long currentUserId) {
        return userId != null && currentUserId != null && userId.equals(currentUserId);
    }
}
