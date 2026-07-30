package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.user.SocialIdentityResolveDTO;
import com.plagod.vo.user.SocialIdentityResolveResultVO;
import com.plagod.vo.user.SocialLoginPrincipalVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "user-service", contextId = "userSocialIdentityClient")
public interface UserSocialIdentityClient {

    @PostMapping("/internal/social-identities/resolve")
    ApiResponse<SocialIdentityResolveResultVO> resolve(@RequestHeader("X-Internal-Token") String internalToken,
                                                       @RequestBody SocialIdentityResolveDTO resolveDTO);

    @GetMapping("/internal/social-identities/users/{userId}/principal")
    ApiResponse<SocialLoginPrincipalVO> getPrincipal(@RequestHeader("X-Internal-Token") String internalToken,
                                                     @PathVariable("userId") Long userId);
}