package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.AvatarUploadResult;
import com.plagod.dto.user.UserPurgeRequestDTO;
import com.plagod.dto.user.UserUpdateDTO;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.UserRequestContextPolicy;
import com.plagod.service.AvatarStorageService;
import com.plagod.service.UserManageService;
import com.plagod.service.UserOperationRequestService;
import com.plagod.vo.user.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
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

    @Autowired
    private UserRequestContextPolicy contextPolicy;

    @GetMapping("/{userId}")
    public ApiResponse<UserVO> getOwnUser(@PathVariable Long userId,
                                          HttpServletRequest request) {
        TrustedRequestContext context =
                contextPolicy.requireUserActor(request);
        contextPolicy.requireSelf(context, userId);

        return ApiResponse.success(userManageService.getUser(userId));
    }

    @PutMapping("/{userId}")
    public ApiResponse<UserVO> updateOwnUser(@PathVariable Long userId,
                                             HttpServletRequest servletRequest,
                                             @Valid @RequestBody UserUpdateDTO updateDTO) {
        TrustedRequestContext context =
                contextPolicy.requireUserActor(servletRequest);
        contextPolicy.requireSelf(context, userId);

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

        return ApiResponse.success(
                "用户信息修改成功",
                userManageService.updateUser(
                        userId,
                        updateDTO,
                        context.getGlobalRole()));
    }

    @PostMapping("/{userId}/avatar")
    public ApiResponse<AvatarUploadResult> uploadOwnAvatar(@PathVariable Long userId,
                                                           HttpServletRequest request,
                                                           @RequestParam("file") MultipartFile file) {
        TrustedRequestContext context =
                contextPolicy.requireUserActor(request);
        contextPolicy.requireSelf(context, userId);

        AvatarUploadResult result = avatarStorageService.store(userId, file);

        UserUpdateDTO updateDTO = new UserUpdateDTO();
        updateDTO.setAvatar(result.getUrl());

        userManageService.updateUser(
                userId,
                updateDTO,
                context.getGlobalRole());

        return ApiResponse.success("头像上传成功", result);
    }

    @PostMapping("/{userId}/purge-requests")
    public ApiResponse<Long> requestOwnPurge(@PathVariable Long userId,
                                             HttpServletRequest request,
                                             @RequestBody(required = false) UserPurgeRequestDTO purgeRequestDTO) {
        TrustedRequestContext context =
                contextPolicy.requireUserActor(request);
        contextPolicy.requireSelf(context, userId);

        String reason = purgeRequestDTO == null ? null : purgeRequestDTO.getReason();
        String requesterName =
                userManageService.getUser(context.getUserId()).getUsername();

        Long requestId = userOperationRequestService.requestPurge(
                userId,
                context.getUserId(),
                requesterName,
                reason);

        return ApiResponse.success("物理删除申请已提交", requestId);
    }
}
