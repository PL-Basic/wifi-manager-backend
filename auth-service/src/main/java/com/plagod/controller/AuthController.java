package com.plagod.controller;


import com.plagod.dto.*;
import com.plagod.dto.auth.AuthResultDTO;
import com.plagod.dto.auth.LoginDTO;
import com.plagod.enums.LoginStatusEnum;
import com.plagod.enums.RegisterStatusEnum;
import com.plagod.exception.RefreshSessionException;
import com.plagod.service.AuthSessionService;
import com.plagod.service.AccountSwitchService;
import com.plagod.service.RefreshCookieService;
import com.plagod.service.UserService;
import com.plagod.utils.RequestIpUtils;
import com.plagod.vo.AuthSessionIssue;
import com.plagod.vo.LoginResult;
import com.plagod.vo.RegisterResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;
import javax.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthSessionService authSessionService;

    @Autowired
    private RefreshCookieService refreshCookieService;

    @Autowired
    private AccountSwitchService accountSwitchService;

    @PostMapping("/register")
    public ApiResponse<RegisterResult> register(@Valid @RequestBody RegisterDTO registerDTO,
                                                HttpServletRequest request) {
        RegisterResult registerResult = userService.register(registerDTO,RequestIpUtils.getClientIP(request));
        if (registerResult.getStatus() == RegisterStatusEnum.SUCCESS) {
            return ApiResponse.success(registerResult.getMessage(), registerResult);
        }else{
            return ApiResponse.fail(400, registerResult.getMessage(), registerResult);
        }
    }

    @PostMapping("/login")
    public ApiResponse<?> login(@Valid @RequestBody LoginDTO loginDTO,
                                @RequestHeader(value = "X-Client-Instance-Id", required = false) String clientInstanceId,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        LoginResult loginResult = userService.login(loginDTO,RequestIpUtils.getClientIP(request));
        if (loginResult.getStatus() == LoginStatusEnum.SUCCESS) {
            AuthResultDTO authenticated = openSession(
                    loginResult.getData(), clientInstanceId, request, response).getAuthResult();
            return ApiResponse.success(loginResult.getMessage(), authenticated);
        } else if (loginResult.getStatus() == LoginStatusEnum.TENANT_MEMBERSHIP_PENDING) {
            return ApiResponse.success(loginResult.getMessage(), loginResult.getData());
        }else {
            return ApiResponse.fail(400,loginResult.getMessage(),loginResult);
        }

    }

    @PostMapping("/code-login")
    public ApiResponse<?> codeLogin(@Valid @RequestBody LoginByVerifyCodeDTO loginByVerifyCodeDTO,
                                    @RequestHeader(value = "X-Client-Instance-Id", required = false) String clientInstanceId,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        LoginResult loginResult = userService.loginByVerifyCode(loginByVerifyCodeDTO, RequestIpUtils.getClientIP(request));
        if (loginResult.getStatus() == LoginStatusEnum.SUCCESS) {
            AuthResultDTO authenticated = openSession(
                    loginResult.getData(), clientInstanceId, request, response).getAuthResult();
            return ApiResponse.success(loginResult.getMessage(), authenticated);
        } else if (loginResult.getStatus() == LoginStatusEnum.TENANT_MEMBERSHIP_PENDING) {
            return ApiResponse.success(loginResult.getMessage(), loginResult.getData());
        }else {
            return ApiResponse.fail(400,loginResult.getMessage(),loginResult);
        }
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResultDTO> refresh(
            @RequestHeader(value = "X-Client-Instance-Id", required = false) String clientInstanceId,
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = refreshCookie(request);
        try {
            AuthSessionIssue issue = authSessionService.refresh(
                    refreshToken,
                    clientInstanceId,
                    request.getHeader("User-Agent"),
                    RequestIpUtils.getClientIP(request));
            refreshCookieService.write(response, issue.getRefreshToken(), issue.getCookieMaxAge());
            return ApiResponse.success("登录会话已刷新", issue.getAuthResult());
        } catch (RefreshSessionException exception) {
            clearTerminalRefreshCookie(response, exception);
            throw exception;
        }
    }

    @PostMapping("/refresh/step-up")
    public ApiResponse<AuthResultDTO> refreshAfterStepUp(
            @Valid @RequestBody RefreshStepUpRequest stepUpRequest,
            @RequestHeader(value = "X-Client-Instance-Id", required = false) String clientInstanceId,
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            AuthSessionIssue issue = authSessionService.refreshAfterStepUp(
                    refreshCookie(request),
                    stepUpRequest.getTarget(),
                    stepUpRequest.getCode(),
                    clientInstanceId,
                    request.getHeader("User-Agent"),
                    RequestIpUtils.getClientIP(request));
            refreshCookieService.write(response, issue.getRefreshToken(), issue.getCookieMaxAge());
            return ApiResponse.success("登录环境复核成功", issue.getAuthResult());
        } catch (RefreshSessionException exception) {
            clearTerminalRefreshCookie(response, exception);
            throw exception;
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            authSessionService.logout(refreshCookie(request), "USER_LOGOUT");
            return ApiResponse.success("已退出登录", null);
        } finally {
            refreshCookieService.clear(response);
        }
    }

    @PostMapping("/account-switch")
    public ApiResponse<AuthResultDTO> switchAccount(
            @Valid @RequestBody AccountSwitchRequest switchRequest,
            @RequestHeader("X-Session-Id") String sessionId,
            @RequestHeader(value = "X-Client-Instance-Id", required = false) String clientInstanceId,
            HttpServletRequest request,
            HttpServletResponse response) {
        String currentRefreshToken = refreshCookie(request);
        if (currentRefreshToken == null || currentRefreshToken.trim().isEmpty()) {
            throw com.plagod.exception.ApiStatusException.conflict(
                    "当前 Refresh Session 不存在，请重新登录后再切换账号");
        }
        AuthResultDTO verifiedIdentity = accountSwitchService.verify(
                switchRequest,
                RequestIpUtils.getClientIP(request));
        try {
            AuthSessionIssue issue = authSessionService.replace(
                    currentRefreshToken,
                    sessionId,
                    verifiedIdentity,
                    clientInstanceId,
                    request.getHeader("User-Agent"),
                    RequestIpUtils.getClientIP(request));
            refreshCookieService.write(
                    response,
                    issue.getRefreshToken(),
                    issue.getCookieMaxAge());
            return ApiResponse.success("账号切换成功", issue.getAuthResult());
        } catch (RefreshSessionException exception) {
            clearTerminalRefreshCookie(response, exception);
            throw exception;
        }
    }

    @PostMapping("/account-switch/codes")
    public ApiResponse<com.plagod.vo.AccountSwitchCodeVO> sendAccountSwitchCode(
            @Valid @RequestBody AccountSwitchCodeRequest codeRequest,
            HttpServletRequest request) {
        return ApiResponse.success(accountSwitchService.sendCode(
                codeRequest,
                RequestIpUtils.getClientIP(request)));
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO resetPasswordDTO,
                                                       HttpServletRequest request) {
        userService.resetPassword(resetPasswordDTO,RequestIpUtils.getClientIP(request));
        return ApiResponse.success("重置密码成功",null);
    }

    private AuthSessionIssue openSession(AuthResultDTO identity,
                                         String clientInstanceId,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        AuthSessionIssue issue = authSessionService.open(
                identity,
                clientInstanceId,
                request.getHeader("User-Agent"),
                RequestIpUtils.getClientIP(request));
        refreshCookieService.write(response, issue.getRefreshToken(), issue.getCookieMaxAge());
        return issue;
    }

    private String refreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (refreshCookieService.getCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void clearTerminalRefreshCookie(HttpServletResponse response,
                                            RefreshSessionException exception) {
        if (exception.getHttpStatus() == 401) {
            refreshCookieService.clear(response);
        }
    }
}
