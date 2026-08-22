package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.UserRequestContextPolicy;
import com.plagod.service.SocialIdentityService;
import com.plagod.vo.user.SocialIdentityVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/users/{userId}/social-identities")
public class SocialIdentityController {

    @Autowired
    private SocialIdentityService socialIdentityService;

    @Autowired
    private UserRequestContextPolicy contextPolicy;

    @GetMapping
    public ApiResponse<List<SocialIdentityVO>> list(@PathVariable Long userId,
                                                    HttpServletRequest request) {
        TrustedRequestContext context =
                contextPolicy.requireUserActor(request);
        contextPolicy.requireSelf(context, userId);
        return ApiResponse.success(socialIdentityService.listOwnedIdentities(userId));
    }

    @DeleteMapping("/{identityId}")
    public ApiResponse<Void> unbind(@PathVariable Long userId,
                                    @PathVariable Long identityId,
                                    HttpServletRequest request) {
        TrustedRequestContext context =
                contextPolicy.requireUserActor(request);
        contextPolicy.requireSelf(context, userId);
        socialIdentityService.unbindOwnedIdentity(userId, identityId);
        return ApiResponse.success("社交身份解绑成功", null);
    }
}
