package com.plagod.service.impl;

import com.plagod.audit.Audited;
import com.plagod.dto.*;
import com.plagod.dto.auth.AuthResultDTO;
import com.plagod.dto.auth.LoginDTO;
import com.plagod.dto.user.UserAccountCreateRequest;
import com.plagod.dto.user.UserPasswordReplaceRequest;
import com.plagod.enums.ConflictFieldEnum;
import com.plagod.enums.LoginStatusEnum;
import com.plagod.exception.ApiStatusException;
import com.plagod.service.LoginFailProtectionService;
import com.plagod.service.AuthSessionService;
import com.plagod.service.UserAccountGateway;
import com.plagod.service.UserService;
import com.plagod.service.VerificationCodeService;
import com.plagod.utils.PasswordUtils;
import com.plagod.vo.LoginResult;
import com.plagod.vo.RegisterResult;
import com.plagod.vo.user.UserAccountCreateResultVO;
import com.plagod.vo.user.UserAccountSnapshotVO;
import com.plagod.vo.user.UserAuthenticationSnapshotVO;
import com.plagod.vo.user.UserPasswordReplaceResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Autowired
    private UserAccountGateway userAccountGateway;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private LoginFailProtectionService loginFailProtectionService;

    @Autowired
    private AuthSessionService authSessionService;

    @Override
    @Audited(
            action = "auth.register",
            scope = Audited.Scope.PLATFORM,
            tenantIdSource = Audited.TenantIdSource.REQUEST,
            includeArgs = false,
            includeResult = false)
    public RegisterResult register(RegisterDTO registerDTO, String requestId, String verifyIp) {
        String fingerprint = registerFingerprint(registerDTO);
        String idempotencyKey = StringUtils.hasText(requestId) ? sha256("REGISTER_REQUEST|" + requestId.trim()) : sha256("REGISTER|" + fingerprint);
        RegisterResult checkResult = checkRegisterContact(
                registerDTO,
                idempotencyKey,
                fingerprint);
        if (checkResult != null){
            return checkResult;
        }
        // 验证码先绑定稳定命令键；User 不可用时同一请求可以重放，不会重复消费。
        consumeRegisterContact(
                registerDTO,
                verifyIp,
                idempotencyKey,
                fingerprint);

        UserAccountCreateRequest request = new UserAccountCreateRequest();
        request.setIdempotencyKey(idempotencyKey);
        request.setRequestFingerprint(fingerprint);
        request.setUsername(registerDTO.getUsername());
        request.setPasswordHash(PasswordUtils.encode(registerDTO.getPassword()));
        request.setNickname(registerDTO.getNickname());
        request.setEmail(registerDTO.getEmail());
        request.setPhone(registerDTO.getPhone());
        UserAccountCreateResultVO result;
        try {
            result = userAccountGateway.create(request);
        } catch (RuntimeException exception) {
            throw userAccountGateway.mapFailure("账号创建", exception);
        }
        if ("FINGERPRINT_CONFLICT".equals(result.getStatus())) {
            return RegisterResult.conflict(
                    EnumSet.noneOf(ConflictFieldEnum.class),
                    result.getMessage());
        }
        if ("CONFLICT".equals(result.getStatus())) {
            return RegisterResult.conflict(toConflictFields(result.getConflictFields()));
        }
        if (!"SUCCESS".equals(result.getStatus()) || result.getAccount() == null) {
            throw new IllegalStateException("账号创建返回未知状态");
        }

        try {
            userAccountGateway.dispatchDefaultMembership(
                    result.getAccount().getUserId());
        } catch (RuntimeException exception) {
            log.warn("默认租户成员事件等待后续投递：userId={}",
                    result.getAccount().getUserId());
        }
        return RegisterResult.success();
    }


    @Override
    public LoginResult login(LoginDTO loginDTO, String requestIp){
        //判断使用的是什么登录
        String account = loginDTO.getAccount();
        String loginType = loginDTO.getLoginType();
        UserAuthenticationSnapshotVO user;
        if("username".equals(loginType)){
            user = findLoginUser("username", account);
        }else if("contact".equals(loginType)){
            user = findLoginUser("contact", account);
        }else {
            throw new RuntimeException("登录类型错误");
        }

        //判断输入的内容是否是正确的，存在的
        if (user == null) {
            return LoginResult.fail(LoginStatusEnum.ACCOUNT_NOT_FOUND,"账号不存在");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            return LoginResult.fail(LoginStatusEnum.ACCOUNT_DISABLED,"账号被禁用");
        }

        //判断账号是否被锁
        try {
            loginFailProtectionService.checkLocked(account,loginType,requestIp);
        } catch (IllegalArgumentException e) {
            return LoginResult.fail(LoginStatusEnum.ACCOUNT_LOCKED,e.getMessage());
        }

        if(!PasswordUtils.matches(loginDTO.getPassword(), user.getPasswordHash())){
            //记录密码错误的失败次数
            loginFailProtectionService.recordFailure(account,loginType,requestIp);
            return LoginResult.fail(LoginStatusEnum.PASSWORD_ERROR,"密码错误");
        }
        //清空密码错误的失败次数
        loginFailProtectionService.clearFailure(account,loginType,requestIp);

        return buildLoginResult(user);
    }

    //验证码登录
    @Override
    public LoginResult loginByVerifyCode(LoginByVerifyCodeDTO loginByVerifyCodeDTO, String verifyIp) {
        String target = loginByVerifyCodeDTO.getTarget();

        try {
            //先验证验证码
            verificationCodeService.consumeCode(target,"login", loginByVerifyCodeDTO.getCode(),verifyIp);
        } catch (IllegalArgumentException e) {
            return LoginResult.fail(LoginStatusEnum.PASSWORD_ERROR,e.getMessage());
        }

        //再判断账号是否存在，避免泄露账号信息
        UserAuthenticationSnapshotVO user =
                findLoginUser("contact", target);
        if (user == null) {
            return LoginResult.fail(LoginStatusEnum.ACCOUNT_NOT_FOUND,"账号不存在");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())){
            return LoginResult.fail(LoginStatusEnum.ACCOUNT_DISABLED,"账号被禁用");
        }

        return buildLoginResult(user);
    }



    @Override
    @Audited(
            action = "auth.reset_password",
            scope = Audited.Scope.PLATFORM,
            tenantIdSource = Audited.TenantIdSource.REQUEST,
            includeArgs = false,
            includeResult = false)
    public void resetPassword(ResetPasswordDTO resetPasswordDTO, String verifyIp) {
        String idempotencyKey = sha256(
                "PASSWORD_RESET|"
                        + normalize(resetPasswordDTO.getTarget()) + "|"
                        + normalize(resetPasswordDTO.getCode()) + "|"
                        + resetPasswordDTO.getNewPassword());
        String consumptionKey = verificationConsumptionKey(
                "reset_password",
                idempotencyKey,
                idempotencyKey,
                resetPasswordDTO.getTarget());

        boolean replaying = verificationCodeService.checkCodeForRequest(
                resetPasswordDTO.getTarget(),
                "reset_password",
                resetPasswordDTO.getCode(),
                consumptionKey);

        UserAuthenticationSnapshotVO user =
                findLoginUser("contact", resetPasswordDTO.getTarget());

        if (user == null) {
            throw new IllegalArgumentException("账号不存在");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())){
            throw new IllegalArgumentException("账号已被禁用");
        }
        if (!replaying && PasswordUtils.matches(
                resetPasswordDTO.getNewPassword(),
                user.getPasswordHash())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }

        String newHash = PasswordUtils.encode(resetPasswordDTO.getNewPassword());
        UserPasswordReplaceRequest request = new UserPasswordReplaceRequest();
        request.setIdempotencyKey(idempotencyKey);
        request.setRequestFingerprint(sha256(
                user.getUserId() + "|"
                        + resetPasswordDTO.getTarget().trim() + "|"
                        + resetPasswordDTO.getNewPassword() + "|"
                        + resetPasswordDTO.getCode()));
        request.setUserId(user.getUserId());
        request.setExpectedPasswordHash(user.getPasswordHash());
        request.setNewPasswordHash(newHash);
        verificationCodeService.consumeCodeForRequest(
                resetPasswordDTO.getTarget(),
                "reset_password",
                resetPasswordDTO.getCode(),
                verifyIp,
                consumptionKey
        );
        UserPasswordReplaceResultVO result;
        try {
            result = userAccountGateway.replacePassword(request);
        } catch (RuntimeException exception) {
            throw userAccountGateway.mapFailure("密码修改", exception);
        }
        if ("CONFLICT".equals(result.getStatus())
                || "FINGERPRINT_CONFLICT".equals(result.getStatus())) {
            throw new IllegalStateException("账号密码已发生变化，请重新发起重置");
        }
        if (!"REPLACED".equals(result.getStatus())) {
            throw new IllegalStateException("密码修改返回未知状态");
        }
        try {
            authSessionService.revokeAllForUser(
                    user.getUserId(),
                    "PASSWORD_CHANGED");
        } catch (RuntimeException exception) {
            throw ApiStatusException.serviceUnavailable(
                    "密码已修改，但旧登录会话撤销未完成，请使用相同请求重试");
        }

    }

    private boolean isPhone(String value) {
        return value != null && PHONE_PATTERN.matcher(value).matches();
    }

    private boolean isEmail(String value) {
        return value != null && EMAIL_PATTERN.matcher(value).matches();
    }
    //处理账号类型的方法。
//    private User findLoginUser(String account) {
//        if (isPhone(account)) {
//            User user = findByField("phone", account);
//            return user != null ? user : findByField("username", account);
//        }
//        if (isEmail(account)) {
//            User user = findByField("email", account);
//            return user != null ? user : findByField("username", account);
//        }
//        return findByField("username", account);
//    }

    private UserAuthenticationSnapshotVO findLoginUser(
            String loginType,
            String account) {
        try {
            return userAccountGateway.findByLogin(loginType, account);
        } catch (RuntimeException exception) {
            throw userAccountGateway.mapFailure("账号读取", exception);
        }
    }

    private LoginResult buildLoginResult(UserAccountSnapshotVO user) {
        if (!Integer.valueOf(0).equals(user.getRole())
                && !Boolean.TRUE.equals(user.getMembershipReady())) {
            AuthResultDTO pending = basicAuthResult(user);
            pending.setAccountState("TENANT_MEMBERSHIP_PENDING");
            return LoginResult.tenantMembershipPending(pending);
        }

        AuthResultDTO authResultDTO = basicAuthResult(user);
        authResultDTO.setAccountState("ACTIVE");

        return LoginResult.success(authResultDTO);
    }

    private AuthResultDTO basicAuthResult(UserAccountSnapshotVO user) {
        AuthResultDTO result = new AuthResultDTO();
        result.setUserId(String.valueOf(user.getUserId()));
        result.setUsername(user.getUsername());
        result.setRole(user.getRole());
        result.setNickname(user.getNickname());
        result.setAvatar(user.getAvatar());
        return result;
    }

    private Set<ConflictFieldEnum> toConflictFields(Set<String> fields) {
        Set<ConflictFieldEnum> result = EnumSet.noneOf(ConflictFieldEnum.class);
        if (fields == null) {
            return result;
        }
        for (String field : fields) {
            try {
                result.add(ConflictFieldEnum.valueOf(field));
            } catch (IllegalArgumentException ignored) {
                log.warn("User账号创建返回未知冲突字段：{}", field);
            }
        }
        return result;
    }

    private String registerFingerprint(RegisterDTO dto) {
        return sha256(
                normalize(dto.getUsername()) + "\n"
                        + normalize(dto.getNickname()) + "\n"
                        + normalize(dto.getEmail()) + "\n"
                        + normalize(dto.getPhone()) + "\n"
                        + dto.getPassword() + "\n"
                        + normalize(dto.getEmailCode()) + "\n"
                        + normalize(dto.getPhoneCode()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                result.append(String.format("%02x", current & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    //判断是否填入邮箱和手机号，并进行验证
    private RegisterResult checkRegisterContact(
            RegisterDTO registerDTO,
            String idempotencyKey,
            String fingerprint) {
        boolean hasPhone = StringUtils.hasText(registerDTO.getPhone());
        boolean hasEmail = StringUtils.hasText(registerDTO.getEmail());

        if (!hasPhone && !hasEmail) {
            return RegisterResult.fail("请至少绑定手机号或邮箱");
        }

        if (hasEmail) {
            if (!StringUtils.hasText(registerDTO.getEmailCode())) {
                return RegisterResult.fail("请输入邮箱验证码");
            }
            try {
                verificationCodeService.checkCodeForRequest(
                        registerDTO.getEmail(),
                        "register",
                        registerDTO.getEmailCode(),
                        verificationConsumptionKey(
                                "register",
                                idempotencyKey,
                                fingerprint,
                                registerDTO.getEmail())
                );
            } catch (IllegalArgumentException e) {
                return RegisterResult.fail(e.getMessage());
            }
        }

        if (hasPhone) {
            if (!StringUtils.hasText(registerDTO.getPhoneCode())) {
                return RegisterResult.fail("请输入手机号验证码");
            }
            try {
                verificationCodeService.checkCodeForRequest(
                  registerDTO.getPhone(),
                  "register",
                  registerDTO.getPhoneCode(),
                  verificationConsumptionKey(
                          "register",
                          idempotencyKey,
                          fingerprint,
                          registerDTO.getPhone())
                );
            } catch (IllegalArgumentException e) {
                return RegisterResult.fail(e.getMessage());
            }
        }
        return null;
    }

    private void consumeRegisterContact(
            RegisterDTO registerDTO,
            String verifyIp,
            String idempotencyKey,
            String fingerprint) {
        if(StringUtils.hasText(registerDTO.getEmail())){
            verificationCodeService.consumeCodeForRequest(
                    registerDTO.getEmail(),
                    "register",
                    registerDTO.getEmailCode(),
                    verifyIp,
                    verificationConsumptionKey(
                            "register",
                            idempotencyKey,
                            fingerprint,
                            registerDTO.getEmail())
            );
        }
        if (StringUtils.hasText(registerDTO.getPhone())){
            verificationCodeService.consumeCodeForRequest(
                        registerDTO.getPhone(),
                        "register",
                        registerDTO.getPhoneCode(),
                        verifyIp,
                        verificationConsumptionKey(
                                "register",
                                idempotencyKey,
                                fingerprint,
                                registerDTO.getPhone())
            );
        }
    }

    private String verificationConsumptionKey(
            String scene,
            String idempotencyKey,
            String requestFingerprint,
            String target) {
        return sha256(scene + "|" + idempotencyKey + "|"
                + requestFingerprint + "|"
                + normalize(target).toLowerCase(java.util.Locale.ROOT));
    }

}
