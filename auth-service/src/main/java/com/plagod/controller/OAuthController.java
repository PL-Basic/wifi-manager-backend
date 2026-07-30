package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.vo.*;
import com.plagod.service.OAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/oauth")
public class OAuthController {

    @Autowired
    private OAuthService oauthService;

    @GetMapping("/{provider}/authorize")
    public ApiResponse<OAuthAuthorizationVO> authorize(@PathVariable String provider,
                                                       @RequestParam(value = "returnUri", required = false) String returnUri) {

        return ApiResponse.success(oauthService.startLogin(provider, returnUri));
    }

    @GetMapping("/{provider}/bind")
    public ApiResponse<OAuthAuthorizationVO> bind(@PathVariable String provider,
                                                  @RequestHeader("X-User-Id") Long userId,
                                                  @RequestParam(value = "returnUri", required = false) String returnUri) {

        return ApiResponse.success(oauthService.startBind(provider, userId, returnUri));
    }

    @GetMapping("/{provider}/callback")
    public ApiResponse<OAuthCallbackResultVO> callback(@PathVariable String provider,
                                                       @RequestParam String state,
                                                       @RequestParam(value = "code", required = false) String code,
                                                       @RequestParam(value = "error", required = false) String error) {

        if (StringUtils.hasText(error)) {
            oauthService.deny(provider, state);
            throw new IllegalArgumentException("用户取消或 Provider 拒绝授权");
        }

        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("OAuth 回调缺少授权码");
        }

        return ApiResponse.success(oauthService.callback(provider, state, code));
    }
}