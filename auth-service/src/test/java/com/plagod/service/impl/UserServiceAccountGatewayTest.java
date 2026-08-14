package com.plagod.service.impl;

import com.plagod.dto.RegisterDTO;
import com.plagod.dto.ResetPasswordDTO;
import com.plagod.dto.auth.LoginDTO;
import com.plagod.enums.LoginStatusEnum;
import com.plagod.enums.RegisterStatusEnum;
import com.plagod.exception.ApiStatusException;
import com.plagod.service.AuthSessionService;
import com.plagod.service.LoginFailProtectionService;
import com.plagod.service.UserAccountGateway;
import com.plagod.service.VerificationCodeService;
import com.plagod.utils.PasswordUtils;
import com.plagod.vo.LoginResult;
import com.plagod.vo.RegisterResult;
import com.plagod.vo.user.UserAccountCreateResultVO;
import com.plagod.vo.user.UserAccountSnapshotVO;
import com.plagod.vo.user.UserAuthenticationSnapshotVO;
import com.plagod.vo.user.UserPasswordReplaceResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceAccountGatewayTest {

    private UserAccountGateway userAccountGateway;
    private VerificationCodeService verificationCodeService;
    private LoginFailProtectionService loginFailProtectionService;
    private AuthSessionService authSessionService;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        userAccountGateway = mock(UserAccountGateway.class);
        verificationCodeService = mock(VerificationCodeService.class);
        loginFailProtectionService = mock(LoginFailProtectionService.class);
        authSessionService = mock(AuthSessionService.class);
        service = new UserServiceImpl();
        ReflectionTestUtils.setField(
                service,
                "userAccountGateway",
                userAccountGateway);
        ReflectionTestUtils.setField(
                service,
                "verificationCodeService",
                verificationCodeService);
        ReflectionTestUtils.setField(
                service,
                "loginFailProtectionService",
                loginFailProtectionService);
        ReflectionTestUtils.setField(
                service,
                "authSessionService",
                authSessionService);
    }

    @Test
    void registerBindsVerificationBeforeAccountCommandAndDispatchesOutbox() {
        RegisterDTO dto = registerDto();
        UserAccountSnapshotVO account = account(true);
        when(userAccountGateway.create(any()))
                .thenReturn(UserAccountCreateResultVO.success(account, false));

        RegisterResult result =
                service.register(dto, "request-7", "192.168.1.23");

        assertEquals(RegisterStatusEnum.SUCCESS, result.getStatus());
        InOrder order = inOrder(verificationCodeService, userAccountGateway);
        order.verify(verificationCodeService).checkCodeForRequest(
                eq("alice@example.com"),
                eq("register"),
                eq("ABC123"),
                anyString());
        order.verify(verificationCodeService).consumeCodeForRequest(
                eq("alice@example.com"),
                eq("register"),
                eq("ABC123"),
                eq("192.168.1.23"),
                anyString());
        order.verify(userAccountGateway).create(any());
        order.verify(userAccountGateway).dispatchDefaultMembership(7L);

        ArgumentCaptor<com.plagod.dto.user.UserAccountCreateRequest> captor =
                ArgumentCaptor.forClass(
                        com.plagod.dto.user.UserAccountCreateRequest.class);
        verify(userAccountGateway).create(captor.capture());
        assertEquals(64, captor.getValue().getIdempotencyKey().length());
        org.junit.jupiter.api.Assertions.assertNotEquals(
                "request-7",
                captor.getValue().getIdempotencyKey());
    }

    @Test
    void registerFailureKeepsStableVerificationBindingForRetry() {
        when(userAccountGateway.create(any()))
                .thenThrow(new IllegalStateException("user unavailable"))
                .thenReturn(UserAccountCreateResultVO.success(
                        account(false),
                        false));
        when(userAccountGateway.mapFailure(anyString(), any()))
                .thenReturn(new IllegalStateException("mapped"));

        assertThrows(
                IllegalStateException.class,
                () -> service.register(
                        registerDto(),
                        "request-7",
                        "192.168.1.23"));
        RegisterResult retry = service.register(
                registerDto(),
                "request-7",
                "192.168.1.23");

        assertEquals(RegisterStatusEnum.SUCCESS, retry.getStatus());
        ArgumentCaptor<String> keyCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(verificationCodeService,
                org.mockito.Mockito.times(2)).consumeCodeForRequest(
                org.mockito.ArgumentMatchers.eq("alice@example.com"),
                org.mockito.ArgumentMatchers.eq("register"),
                org.mockito.ArgumentMatchers.eq("ABC123"),
                org.mockito.ArgumentMatchers.eq("192.168.1.23"),
                keyCaptor.capture());
        assertEquals(
                keyCaptor.getAllValues().get(0),
                keyCaptor.getAllValues().get(1));
    }

    @Test
    void changedRegistrationPayloadCannotReuseConsumedVerificationBinding() {
        when(userAccountGateway.create(any()))
                .thenThrow(new IllegalStateException("user unavailable"));
        when(userAccountGateway.mapFailure(anyString(), any()))
                .thenReturn(new IllegalStateException("mapped"));

        RegisterDTO first = registerDto();
        RegisterDTO changed = registerDto();
        changed.setPassword("different-password");

        assertThrows(
                IllegalStateException.class,
                () -> service.register(
                        first,
                        "request-7",
                        "192.168.1.23"));
        assertThrows(
                IllegalStateException.class,
                () -> service.register(
                        changed,
                        "request-7",
                        "192.168.1.23"));

        ArgumentCaptor<String> keyCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(verificationCodeService,
                org.mockito.Mockito.times(2)).consumeCodeForRequest(
                eq("alice@example.com"),
                eq("register"),
                eq("ABC123"),
                eq("192.168.1.23"),
                keyCaptor.capture());
        org.junit.jupiter.api.Assertions.assertNotEquals(
                keyCaptor.getAllValues().get(0),
                keyCaptor.getAllValues().get(1));
    }

    @Test
    void registerRemainsSuccessfulWhenImmediateOutboxDispatchFails() {
        UserAuthenticationSnapshotVO account =
                authenticationAccount(false);
        when(userAccountGateway.create(any()))
                .thenReturn(UserAccountCreateResultVO.success(account, false));
        doThrow(new IllegalStateException("tenant unavailable"))
                .when(userAccountGateway)
                .dispatchDefaultMembership(7L);

        RegisterResult result = service.register(
                registerDto(),
                "request-7",
                "192.168.1.23");

        assertEquals(RegisterStatusEnum.SUCCESS, result.getStatus());
    }

    @Test
    void passwordLoginReturnsPendingWithoutIssuingToken() {
        UserAuthenticationSnapshotVO account =
                authenticationAccount(false);
        when(userAccountGateway.findByLogin("username", "alice"))
                .thenReturn(account);

        LoginDTO dto = new LoginDTO();
        dto.setLoginType("username");
        dto.setAccount("alice");
        dto.setPassword("oldpass");
        LoginResult result = service.login(dto, "192.168.1.23");

        assertEquals(LoginStatusEnum.TENANT_MEMBERSHIP_PENDING, result.getStatus());
        assertEquals(
                "TENANT_MEMBERSHIP_PENDING",
                result.getData().getAccountState());
        assertNull(result.getData().getToken());
    }

    @Test
    void resetPasswordBindsCodeThenReplacesPasswordAndRevokesSessions() {
        UserAuthenticationSnapshotVO account =
                authenticationAccount(true);
        when(userAccountGateway.findByLogin(
                "contact",
                "alice@example.com")).thenReturn(account);
        when(userAccountGateway.replacePassword(any()))
                .thenReturn(UserPasswordReplaceResultVO.replaced(false));

        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setTarget("alice@example.com");
        dto.setCode("ABC123");
        dto.setNewPassword("newpass");
        service.resetPassword(dto, "192.168.1.23");

        InOrder order = inOrder(
                userAccountGateway,
                authSessionService,
                verificationCodeService);
        order.verify(verificationCodeService).checkCodeForRequest(
                eq("alice@example.com"),
                eq("reset_password"),
                eq("ABC123"),
                anyString());
        order.verify(verificationCodeService).consumeCodeForRequest(
                eq("alice@example.com"),
                eq("reset_password"),
                eq("ABC123"),
                eq("192.168.1.23"),
                anyString());
        order.verify(userAccountGateway).replacePassword(any());
        order.verify(authSessionService).revokeAllForUser(
                7L,
                "PASSWORD_CHANGED");

        ArgumentCaptor<com.plagod.dto.user.UserPasswordReplaceRequest> captor =
                ArgumentCaptor.forClass(
                        com.plagod.dto.user.UserPasswordReplaceRequest.class);
        verify(userAccountGateway).replacePassword(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(account.getPasswordHash(),
                captor.getValue().getExpectedPasswordHash());
    }

    @Test
    void resetPasswordRetryCompletesSessionRevocationAfterPartialFailure() {
        UserAuthenticationSnapshotVO before =
                authenticationAccount(true);
        UserAuthenticationSnapshotVO after =
                authenticationAccount(true);
        after.setPasswordHash(PasswordUtils.encode("newpass"));
        when(userAccountGateway.findByLogin(
                "contact",
                "alice@example.com")).thenReturn(before, after);
        when(userAccountGateway.replacePassword(any())).thenReturn(
                UserPasswordReplaceResultVO.replaced(false),
                UserPasswordReplaceResultVO.replaced(true));
        when(verificationCodeService.checkCodeForRequest(
                anyString(),
                anyString(),
                anyString(),
                anyString())).thenReturn(false, true);
        doThrow(new IllegalStateException("session store unavailable"))
                .doNothing()
                .when(authSessionService)
                .revokeAllForUser(7L, "PASSWORD_CHANGED");

        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setTarget("alice@example.com");
        dto.setCode("ABC123");
        dto.setNewPassword("newpass");

        ApiStatusException partialFailure = assertThrows(
                ApiStatusException.class,
                () -> service.resetPassword(dto, "192.168.1.23"));
        assertEquals(503, partialFailure.getHttpStatus());
        assertEquals(
                "密码已修改，但旧登录会话撤销未完成，请使用相同请求重试",
                partialFailure.getMessage());
        service.resetPassword(dto, "192.168.1.23");

        verify(userAccountGateway,
                org.mockito.Mockito.times(2)).replacePassword(any());
        verify(authSessionService,
                org.mockito.Mockito.times(2)).revokeAllForUser(
                7L,
                "PASSWORD_CHANGED");
        verify(verificationCodeService,
                org.mockito.Mockito.times(2)).consumeCodeForRequest(
                eq("alice@example.com"),
                eq("reset_password"),
                eq("ABC123"),
                eq("192.168.1.23"),
                anyString());
    }

    @Test
    void resetPasswordRejectsFirstAttemptWithCurrentPassword() {
        UserAuthenticationSnapshotVO account =
                authenticationAccount(true);
        when(userAccountGateway.findByLogin(
                "contact",
                "alice@example.com")).thenReturn(account);
        when(verificationCodeService.checkCodeForRequest(
                anyString(),
                anyString(),
                anyString(),
                anyString())).thenReturn(false);

        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setTarget("alice@example.com");
        dto.setCode("ABC123");
        dto.setNewPassword("oldpass");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.resetPassword(dto, "192.168.1.23"));
        assertEquals("新密码不能与当前密码相同", exception.getMessage());
        verify(userAccountGateway, never()).replacePassword(any());
        verify(authSessionService, never())
                .revokeAllForUser(anyLong(), anyString());
        verify(verificationCodeService, never()).consumeCodeForRequest(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString());
    }

    private RegisterDTO registerDto() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("alice");
        dto.setPassword("oldpass");
        dto.setNickname("Alice");
        dto.setEmail("alice@example.com");
        dto.setEmailCode("ABC123");
        return dto;
    }

    private UserAccountSnapshotVO account(boolean membershipReady) {
        UserAccountSnapshotVO account = new UserAccountSnapshotVO();
        account.setUserId(7L);
        account.setUsername("alice");
        account.setNickname("Alice");
        account.setRole(2);
        account.setStatus(1);
        account.setMembershipReady(membershipReady);
        return account;
    }

    private UserAuthenticationSnapshotVO authenticationAccount(
            boolean membershipReady) {
        UserAuthenticationSnapshotVO account =
                new UserAuthenticationSnapshotVO();
        account.setUserId(7L);
        account.setUsername("alice");
        account.setNickname("Alice");
        account.setRole(2);
        account.setStatus(1);
        account.setMembershipReady(membershipReady);
        account.setPasswordHash(PasswordUtils.encode("oldpass"));
        return account;
    }

}
