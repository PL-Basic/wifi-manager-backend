package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.user.UserAccountCreateRequest;
import com.plagod.dto.user.UserPasswordReplaceRequest;
import com.plagod.service.UserAccountPersistenceService;
import com.plagod.vo.user.UserAccountCreateResultVO;
import com.plagod.vo.user.UserAccountSnapshotVO;
import com.plagod.vo.user.UserAuthenticationSnapshotVO;
import com.plagod.vo.user.UserPasswordReplaceResultVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/internal/user-accounts")
public class InternalUserAccountController {

    private final UserAccountPersistenceService accountService;

    public InternalUserAccountController(UserAccountPersistenceService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ApiResponse<UserAccountCreateResultVO> create(
            @Valid @RequestBody UserAccountCreateRequest request) {
        return ApiResponse.success(accountService.createAccount(request));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserAccountSnapshotVO> findById(@PathVariable Long userId) {
        return ApiResponse.success(accountService.findById(userId));
    }

    @GetMapping("/{userId}/authentication")
    public ApiResponse<UserAuthenticationSnapshotVO> findAuthenticationById(
            @PathVariable Long userId) {
        return ApiResponse.success(
                accountService.findAuthenticationById(userId));
    }

    @GetMapping("/login")
    public ApiResponse<UserAuthenticationSnapshotVO> findByLogin(
            @RequestParam String loginType,
            @RequestParam String account) {
        return ApiResponse.success(accountService.findByLogin(loginType, account));
    }

    @PostMapping("/password")
    public ApiResponse<UserPasswordReplaceResultVO> replacePassword(
            @Valid @RequestBody UserPasswordReplaceRequest request) {
        return ApiResponse.success(accountService.replacePassword(request));
    }

    @PostMapping("/{userId}/default-membership/dispatch")
    public ApiResponse<Void> dispatchDefaultMembership(@PathVariable Long userId) {
        accountService.dispatchDefaultMembership(userId);
        return ApiResponse.success(null);
    }
}
