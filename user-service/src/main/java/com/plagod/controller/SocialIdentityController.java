package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.SocialIdentityService;
import com.plagod.vo.user.SocialIdentityVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}/social-identities")
public class SocialIdentityController {

    @Autowired
    private SocialIdentityService socialIdentityService;

    @GetMapping
    public ApiResponse<List<SocialIdentityVO>> list(@PathVariable Long userId,
                                                    @RequestHeader("X-User-Id") Long currentUserId) {

        requireSelf(userId, currentUserId);
        return ApiResponse.success(socialIdentityService.listOwnedIdentities(userId));
    }

    @DeleteMapping("/{identityId}")
    public ApiResponse<Void> unbind(@PathVariable Long userId,
                                    @PathVariable Long identityId,
                                    @RequestHeader("X-User-Id") Long currentUserId) {

        requireSelf(userId, currentUserId);
        socialIdentityService.unbindOwnedIdentity(userId, identityId);
        return ApiResponse.success("社交身份解绑成功", null);
    }

    private void requireSelf(Long userId, Long currentUserId) {
        if (userId == null || currentUserId == null || !userId.equals(currentUserId)) {
            throw new IllegalArgumentException("只能管理本人绑定的社交身份");
        }
    }
}