package com.plagod.service.impl;

import com.plagod.configuration.AuthSessionProperties;
import com.plagod.dto.auth.OperationTokenConsumeRequest;
import com.plagod.dto.auth.OperationTokenIssueRequest;
import com.plagod.entity.user.User;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.UserMapper;
import com.plagod.service.AuthOperationTokenService;
import com.plagod.service.AuthSessionService;
import com.plagod.service.VerificationCodeService;
import com.plagod.utils.JwtUtils;
import com.plagod.utils.PasswordUtils;
import com.plagod.vo.auth.OperationTokenConsumptionVO;
import com.plagod.vo.auth.OperationTokenVO;
import com.plagod.vo.auth.SessionValidationVO;
import io.jsonwebtoken.Claims;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthOperationTokenServiceImpl implements AuthOperationTokenService {

    private static final String CONSUMED_KEY_PREFIX = "auth:operation-token:consumed:";

    private final UserMapper userMapper;
    private final VerificationCodeService verificationCodeService;
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;
    private final AuthSessionProperties properties;
    private final AuthSessionService authSessionService;

    public AuthOperationTokenServiceImpl(UserMapper userMapper,
                                         VerificationCodeService verificationCodeService,
                                         JwtUtils jwtUtils,
                                         StringRedisTemplate redisTemplate,
                                         AuthSessionProperties properties,
                                         AuthSessionService authSessionService) {
        this.userMapper = userMapper;
        this.verificationCodeService = verificationCodeService;
        this.jwtUtils = jwtUtils;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.authSessionService = authSessionService;
    }

    @Override
    public OperationTokenVO issue(Long userId,
                                  String sessionId,
                                  OperationTokenIssueRequest request,
                                  String clientIp) {
        if (!StringUtils.hasText(sessionId)) {
            throw ApiStatusException.forbidden("当前登录会话不支持高风险操作，请重新登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw ApiStatusException.forbidden("用户当前不可用");
        }
        SessionValidationVO session = authSessionService.validate(
                sessionId,
                userId,
                "operation-issue-" + UUID.randomUUID());
        if (!Boolean.TRUE.equals(session.getActive())) {
            throw ApiStatusException.forbidden("当前登录会话已失效");
        }
        verifyStepUp(user, request, clientIp);
        long validityMillis = properties.getOperationTokenTtl().toMillis();
        String token = jwtUtils.generateOperationToken(
                userId,
                sessionId,
                request.getPurpose(),
                session.getSecurityVersion(),
                validityMillis);
        OperationTokenVO result = new OperationTokenVO();
        result.setToken(token);
        result.setPurpose(request.getPurpose());
        result.setExpiresAt(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(System.currentTimeMillis() + validityMillis),
                ZoneId.systemDefault()));
        return result;
    }

    @Override
    public OperationTokenConsumptionVO consume(OperationTokenConsumeRequest request) {
        Claims claims;
        try {
            claims = jwtUtils.parseToken(request.getToken());
        } catch (RuntimeException exception) {
            throw ApiStatusException.forbidden("一次性操作凭证无效或已经过期");
        }
        if (!jwtUtils.hasExpectedIssuer(claims)
                || !JwtUtils.OPERATION_AUDIENCE.equals(claims.getAudience())) {
            throw ApiStatusException.forbidden("凭证 audience 不允许执行高风险操作");
        }
        Long userId = JwtUtils.getUserId(claims);
        SessionValidationVO session = authSessionService.validate(
                JwtUtils.getSessionId(claims),
                userId,
                claims.getId());
        if (!Boolean.TRUE.equals(session.getActive())) {
            throw ApiStatusException.forbidden("签发该凭证的登录会话已失效");
        }
        if (!Objects.equals(
                JwtUtils.getLongClaim(claims, "sessionSecurityVersion"),
                session.getSecurityVersion())) {
            throw ApiStatusException.forbidden("一次性操作凭证所属安全会话已经变化");
        }
        String purpose = claims.get("purpose", String.class);
        if (!request.getPurpose().equals(purpose)) {
            throw ApiStatusException.forbidden("一次性操作凭证 purpose 不匹配");
        }
        String jti = claims.getId();
        Date expiresAt = claims.getExpiration();
        if (!StringUtils.hasText(jti) || expiresAt == null) {
            throw ApiStatusException.forbidden("一次性操作凭证内容不完整");
        }
        long ttlMillis = expiresAt.getTime() - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            throw ApiStatusException.forbidden("一次性操作凭证已经过期");
        }
        Boolean consumed;
        try {
            consumed = redisTemplate.opsForValue().setIfAbsent(
                    CONSUMED_KEY_PREFIX + jti,
                    request.getPurpose() + ":" + request.getBusinessKey(),
                    ttlMillis,
                    TimeUnit.MILLISECONDS);
        } catch (RuntimeException exception) {
            throw ApiStatusException.serviceUnavailable("一次性操作凭证校验依赖暂时不可用");
        }
        if (!Boolean.TRUE.equals(consumed)) {
            throw ApiStatusException.conflict("一次性操作凭证已经被消费");
        }
        OperationTokenConsumptionVO result = new OperationTokenConsumptionVO();
        result.setUserId(String.valueOf(userId));
        result.setPurpose(purpose);
        result.setBusinessKey(request.getBusinessKey());
        return result;
    }

    private void verifyStepUp(User user, OperationTokenIssueRequest request, String clientIp) {
        if (StringUtils.hasText(request.getPassword())
                && PasswordUtils.matches(request.getPassword(), user.getPassword())) {
            return;
        }
        if (!StringUtils.hasText(request.getTarget()) || !StringUtils.hasText(request.getCode())) {
            throw ApiStatusException.forbidden("请使用当前密码或验证码完成身份复核");
        }
        boolean targetOwned = request.getTarget().equals(user.getPhone())
                || request.getTarget().equalsIgnoreCase(user.getEmail());
        if (!targetOwned) {
            throw ApiStatusException.forbidden("验证码目标不属于当前账号");
        }
        try {
            verificationCodeService.consumeCode(
                    request.getTarget(),
                    "step_up",
                    request.getCode(),
                    clientIp);
        } catch (IllegalArgumentException exception) {
            throw ApiStatusException.forbidden(exception.getMessage());
        }
    }
}
