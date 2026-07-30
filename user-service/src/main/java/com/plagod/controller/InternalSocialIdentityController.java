package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.user.SocialIdentityResolveDTO;
import com.plagod.service.SocialIdentityService;
import com.plagod.vo.user.SocialIdentityResolveResultVO;
import com.plagod.vo.user.SocialLoginPrincipalVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/internal/social-identities")
public class InternalSocialIdentityController {

    @Autowired
    private SocialIdentityService socialIdentityService;

    @PostMapping("/resolve")
    public ApiResponse<SocialIdentityResolveResultVO> resolve(@Valid @RequestBody SocialIdentityResolveDTO resolveDTO) {

        return ApiResponse.success(socialIdentityService.resolve(resolveDTO));
    }

    @GetMapping("/users/{userId}/principal")
    public ApiResponse<SocialLoginPrincipalVO> getPrincipal(@PathVariable Long userId) {

        return ApiResponse.success(socialIdentityService.getLoginPrincipal(userId));
    }
}