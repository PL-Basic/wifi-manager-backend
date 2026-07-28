package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.user.UserOperationReviewDTO;
import com.plagod.dto.user.UserPurgeRequestDTO;
import com.plagod.dto.user.UserStatusDTO;
import com.plagod.dto.user.UserUpdateDTO;
import com.plagod.service.UserManageService;
import com.plagod.service.UserOperationRequestService;
import com.plagod.vo.user.UserOperationRequestPageResult;
import com.plagod.vo.user.UserPageResult;
import com.plagod.vo.user.UserStatsVO;
import com.plagod.vo.user.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/internal/admin/users")
public class InternalAdminUserController {

    @Autowired
    private UserManageService userManageService;

    @Autowired
    private UserOperationRequestService userOperationRequestService;

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
                                          @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                          @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole,
                                          @RequestBody UserUpdateDTO updateDTO) {

        if (updateDTO == null) {
            throw new IllegalArgumentException("用户修改参数不能为空");
        }

        if (isSelf(userId, operatorId) && updateDTO.getRole() != null) {

            throw new IllegalArgumentException("不能修改自己的角色");
        }

        return ApiResponse.success("用户信息修改成功", userManageService.updateUser(userId, updateDTO, operatorRole));
    }

    @PutMapping("/{userId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long userId,
                                          @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                          @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole,
                                          @Valid @RequestBody UserStatusDTO statusDTO) {

        if (isSelf(userId, operatorId) && Integer.valueOf(0).equals(statusDTO.getStatus())) {

            throw new IllegalArgumentException("不能禁用自己的账号");
        }

        rejectPeerAdminModification(userId, operatorRole);

        userManageService.updateStatus(userId, statusDTO);

        return ApiResponse.success("用户状态修改成功", null);
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable Long userId,
                                        @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                        @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole) {

        if (isSelf(userId, operatorId)) {
            throw new IllegalArgumentException("不能逻辑删除自己的账号");
        }

        rejectPeerAdminModification(userId, operatorRole);

        userManageService.deleteUser(userId);

        return ApiResponse.success("用户已逻辑删除", null);
    }

    @DeleteMapping("/{userId}/purge")
    public ApiResponse<Void> purgeUser(@PathVariable Long userId,
                                       @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                       @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole) {

        if (!Integer.valueOf(0).equals(operatorRole)) {
            throw new IllegalArgumentException("只有超级管理员可以直接物理删除");
        }

        if (isSelf(userId, operatorId)) {
            throw new IllegalArgumentException("不能物理删除自己的账号");
        }

        userManageService.purgeUser(userId);

        return ApiResponse.success("用户已物理删除", null);
    }

    @PostMapping("/{userId}/purge-requests")
    public ApiResponse<Long> requestPurgeUser(@PathVariable Long userId,
                                              @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
                                              @RequestHeader(value = "X-User-Name", required = false) String requesterName,
                                              @RequestBody(required = false) UserPurgeRequestDTO purgeRequestDTO) {

        String reason = purgeRequestDTO == null ? null : purgeRequestDTO.getReason();

        Long requestId = userOperationRequestService.requestPurge(userId, requesterId, requesterName, reason);

        return ApiResponse.success("物理删除申请已提交", requestId);
    }

    @GetMapping("/operation-requests")
    public ApiResponse<UserOperationRequestPageResult>
    pageOperationRequests(@RequestParam(defaultValue = "1") Long current,
                          @RequestParam(defaultValue = "10") Long size,
                          @RequestParam(required = false) Integer status) {

        return ApiResponse.success(userOperationRequestService.pageRequests(current, size, status));
    }

    @PutMapping("/operation-requests/{id}/review")
    public ApiResponse<Void> reviewOperationRequest(@PathVariable Long id,
                                                    @RequestHeader(value = "X-User-Id", required = false) Long approverId,
                                                    @RequestHeader(value = "X-User-Name", required = false) String approverName,
                                                    @RequestBody UserOperationReviewDTO dto) {

        userOperationRequestService.review(id, approverId, approverName, dto);

        return ApiResponse.success("审批完成", null);
    }

    private void rejectPeerAdminModification(Long targetUserId, Integer operatorRole) {

        // 超级管理员可以操作管理员；普通管理员不能操作管理员。
        if (!Integer.valueOf(0).equals(operatorRole) && userManageService.getUser(targetUserId).getRole() <= 1) {

            throw new IllegalArgumentException("管理员之间不能互相修改");
        }
    }

    private boolean isSelf(Long userId, Long operatorId) {
        return userId != null && operatorId != null && userId.equals(operatorId);
    }
}