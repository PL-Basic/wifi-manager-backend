package com.plagod.service.impl;

import com.plagod.dto.AccountSwitchCodeRequest;
import com.plagod.dto.AccountSwitchRequest;
import com.plagod.dto.auth.AuthResultDTO;
import com.plagod.exception.ApiStatusException;
import com.plagod.service.AccountSwitchService;
import com.plagod.service.UserAccountGateway;
import com.plagod.service.VerificationCodeService;
import com.plagod.vo.AccountSwitchCodeVO;
import com.plagod.vo.user.UserAccountSnapshotVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AccountSwitchServiceImpl implements AccountSwitchService {

    private final UserAccountGateway userAccountGateway;
    private final VerificationCodeService verificationCodeService;

    public AccountSwitchServiceImpl(
                                    UserAccountGateway userAccountGateway,
                                    VerificationCodeService verificationCodeService) {
        this.userAccountGateway = userAccountGateway;
        this.verificationCodeService = verificationCodeService;
    }

    @Override
    public AccountSwitchCodeVO sendCode(AccountSwitchCodeRequest request, String clientIp) {
        UserAccountSnapshotVO user =
                requireTargetUser(request.getExpectedUserId());
        String target = target(user, request.getChannel());
        verificationCodeService.sendCode(target, "login", clientIp);
        return new AccountSwitchCodeVO(request.getChannel(), mask(target, request.getChannel()));
    }

    @Override
    public AuthResultDTO verify(AccountSwitchRequest request, String clientIp) {
        UserAccountSnapshotVO user =
                requireTargetUser(request.getExpectedUserId());
        String target = target(user, request.getChannel());
        verificationCodeService.consumeCode(target, "login", request.getCode(), clientIp);
        if (!Integer.valueOf(0).equals(user.getRole())
                && !Boolean.TRUE.equals(user.getMembershipReady())) {
            throw ApiStatusException.conflict("目标账号的默认租户成员关系正在恢复");
        }
        AuthResultDTO identity = new AuthResultDTO();
        identity.setUserId(String.valueOf(user.getUserId()));
        identity.setUsername(user.getUsername());
        identity.setNickname(user.getNickname());
        identity.setAvatar(user.getAvatar());
        identity.setRole(user.getRole());
        identity.setAccountState("ACTIVE");
        return identity;
    }

    private UserAccountSnapshotVO requireTargetUser(String userIdValue) {
        Long userId;
        try {
            userId = Long.valueOf(userIdValue);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("目标账号ID无效");
        }
        UserAccountSnapshotVO user;
        try {
            user = userAccountGateway.findById(userId);
        } catch (RuntimeException exception) {
            throw userAccountGateway.mapFailure("账号读取", exception);
        }
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw ApiStatusException.forbidden("目标历史账号当前不可用");
        }
        return user;
    }

    private String target(UserAccountSnapshotVO user, String channel) {
        String target = "phone".equals(channel) ? user.getPhone() : user.getEmail();
        if (!StringUtils.hasText(target)) {
            throw ApiStatusException.conflict("目标历史账号没有可用的" + ("phone".equals(channel) ? "手机号" : "邮箱"));
        }
        return target.trim();
    }

    private String mask(String target, String channel) {
        if ("phone".equals(channel) && target.length() >= 7) {
            return target.substring(0, 3) + "****" + target.substring(target.length() - 4);
        }
        int at = target.indexOf('@');
        if (at > 0) {
            String name = target.substring(0, at);
            String visible = name.substring(0, Math.min(2, name.length()));
            return visible + "***" + target.substring(at);
        }
        return "***";
    }
}
