package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.user.UserAccountCreateRequest;
import com.plagod.dto.user.UserPasswordReplaceRequest;
import com.plagod.vo.user.UserAccountCreateResultVO;
import com.plagod.vo.user.UserAccountSnapshotVO;
import com.plagod.vo.user.UserAuthenticationSnapshotVO;
import com.plagod.vo.user.UserPasswordReplaceResultVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-service", contextId = "userAccountClient")
public interface UserAccountClient {

    @PostMapping("/internal/user-accounts")
    ApiResponse<UserAccountCreateResultVO> create(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody UserAccountCreateRequest request);

    @GetMapping("/internal/user-accounts/{userId}")
    ApiResponse<UserAccountSnapshotVO> findById(
            @RequestHeader("X-Internal-Token") String internalToken,
            @PathVariable("userId") Long userId);

    @GetMapping("/internal/user-accounts/{userId}/authentication")
    ApiResponse<UserAuthenticationSnapshotVO> findAuthenticationById(
            @RequestHeader("X-Internal-Token") String internalToken,
            @PathVariable("userId") Long userId);

    @GetMapping("/internal/user-accounts/login")
    ApiResponse<UserAuthenticationSnapshotVO> findByLogin(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestParam("loginType") String loginType,
            @RequestParam("account") String account);

    @PostMapping("/internal/user-accounts/password")
    ApiResponse<UserPasswordReplaceResultVO> replacePassword(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody UserPasswordReplaceRequest request);

    @PostMapping("/internal/user-accounts/{userId}/default-membership/dispatch")
    ApiResponse<Void> dispatchDefaultMembership(
            @RequestHeader("X-Internal-Token") String internalToken,
            @PathVariable("userId") Long userId);
}
