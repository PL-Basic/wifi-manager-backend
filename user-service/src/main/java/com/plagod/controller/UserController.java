package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.AvatarUploadResult;
import com.plagod.dto.user.UserPurgeRequestDTO;
import com.plagod.dto.user.UserUpdateDTO;
import com.plagod.service.AvatarStorageService;
import com.plagod.service.UserManageService;
import com.plagod.service.UserOperationRequestService;
import com.plagod.vo.user.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
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


    @GetMapping("/{userId}")
    public ApiResponse<UserVO> getOwnUser(@PathVariable Long userId,
                                          @RequestHeader("X-User-Id") Long currentUserId) {

        requireSelf(userId, currentUserId);

        return ApiResponse.success(userManageService.getUser(userId));
    }

    @PutMapping("/{userId}")
    public ApiResponse<UserVO> updateOwnUser(@PathVariable Long userId,
                                             @RequestHeader("X-User-Id") Long currentUserId,
                                             @RequestHeader("X-User-Role") Integer currentRole,
                                             @Valid @RequestBody UserUpdateDTO updateDTO) {

        requireSelf(userId, currentUserId);

        if (updateDTO == null) {
            throw new IllegalArgumentException("用户修改参数不能为空");
        }

        // 本人资料接口只允许修改昵称。
        // 邮箱和手机号必须通过验证码绑定流程修改，头像必须通过上传接口修改。
        updateDTO.setEmail(null);
        updateDTO.setPhone(null);
        updateDTO.setAvatar(null);
        updateDTO.setRole(null);
        updateDTO.setMaxConnections(null);
        updateDTO.setDailyQuotaMinutes(null);
        updateDTO.setExpireTime(null);

        return ApiResponse.success("用户信息修改成功", userManageService.updateUser(userId, updateDTO, currentRole));
    }

    @PostMapping("/{userId}/avatar")
    public ApiResponse<AvatarUploadResult> uploadOwnAvatar(@PathVariable Long userId,
                                                           @RequestHeader("X-User-Id") Long currentUserId,
                                                           @RequestHeader("X-User-Role") Integer currentRole,
                                                           @RequestParam("file") MultipartFile file) {

        requireSelf(userId, currentUserId);

        AvatarUploadResult result = avatarStorageService.store(userId, file);

        UserUpdateDTO updateDTO = new UserUpdateDTO();
        updateDTO.setAvatar(result.getUrl());

        userManageService.updateUser(userId, updateDTO, currentRole);

        return ApiResponse.success("头像上传成功", result);
    }

    @PostMapping("/{userId}/purge-requests")
    public ApiResponse<Long> requestOwnPurge(@PathVariable Long userId,
                                             @RequestHeader("X-User-Id") Long requesterId,
                                             @RequestHeader("X-User-Name") String requesterName,
                                             @RequestBody(required = false) UserPurgeRequestDTO purgeRequestDTO) {

        requireSelf(userId, requesterId);

        String reason = purgeRequestDTO == null ? null : purgeRequestDTO.getReason();

        Long requestId = userOperationRequestService.requestPurge(userId, requesterId, requesterName, reason);

        return ApiResponse.success("物理删除申请已提交", requestId);
    }

    private void requireSelf(Long targetUserId, Long currentUserId) {
        if (targetUserId == null || currentUserId == null || !targetUserId.equals(currentUserId)) {

            throw new IllegalArgumentException("只能访问或修改本人资料");
        }
    }
}