package com.plagod.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.client.TenantContextClient;
import com.plagod.configuration.AuthSessionProperties;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.auth.AuthResultDTO;
import com.plagod.dto.tenant.TenantContextResolveRequest;
import com.plagod.entity.auth.AuthRefreshRiskEvent;
import com.plagod.entity.auth.AuthRefreshSession;
import com.plagod.entity.auth.AuthRefreshToken;
import com.plagod.exception.ApiStatusException;
import com.plagod.exception.RefreshSessionException;
import com.plagod.mapper.AuthRefreshRiskEventMapper;
import com.plagod.mapper.AuthRefreshSessionMapper;
import com.plagod.mapper.AuthRefreshTokenMapper;
import com.plagod.service.AuthSessionService;
import com.plagod.service.UserAccountGateway;
import com.plagod.service.VerificationCodeService;
import com.plagod.utils.JwtUtils;
import com.plagod.vo.AuthSessionIssue;
import com.plagod.vo.auth.SessionValidationVO;
import com.plagod.vo.tenant.TenantContextVO;
import com.plagod.vo.user.UserAccountSnapshotVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import feign.FeignException;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Service
public class AuthSessionServiceImpl implements AuthSessionService {

    private static final String ACTIVE = "ACTIVE";
    private static final String REVOKED_JTI_PREFIX = "auth:access-jti:revoked:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthSessionServiceImpl.class);

    private final AuthRefreshSessionMapper sessionMapper;
    private final AuthRefreshTokenMapper tokenMapper;
    private final AuthRefreshRiskEventMapper riskEventMapper;
    private final UserAccountGateway userAccountGateway;
    private final TenantContextClient tenantContextClient;
    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;
    private final AuthSessionProperties properties;
    private final String internalToken;
    private final StringRedisTemplate redisTemplate;
    private final VerificationCodeService verificationCodeService;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate remoteCallTemplate;

    public AuthSessionServiceImpl(AuthRefreshSessionMapper sessionMapper,
                                  AuthRefreshTokenMapper tokenMapper,
                                  AuthRefreshRiskEventMapper riskEventMapper,
                                  UserAccountGateway userAccountGateway,
                                  TenantContextClient tenantContextClient,
                                  JwtUtils jwtUtils,
                                  ObjectMapper objectMapper,
                                  AuthSessionProperties properties,
                                  StringRedisTemplate redisTemplate,
                                  VerificationCodeService verificationCodeService,
                                  PlatformTransactionManager transactionManager,
                                  @Value("${wifi.internal.token}") String internalToken) {
        this.sessionMapper = sessionMapper;
        this.tokenMapper = tokenMapper;
        this.riskEventMapper = riskEventMapper;
        this.userAccountGateway = userAccountGateway;
        this.tenantContextClient = tenantContextClient;
        this.jwtUtils = jwtUtils;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.verificationCodeService = verificationCodeService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.remoteCallTemplate = new TransactionTemplate(transactionManager);
        this.remoteCallTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        this.internalToken = internalToken;
    }

    @Override
    public AuthSessionIssue open(AuthResultDTO identity,
                                 String clientInstanceId,
                                 String userAgent,
                                 String clientIp) {
        Long userId = parsePositiveId(identity == null ? null : identity.getUserId(), "用户ID");
        UserAccountSnapshotVO user = requireActiveUser(userId);
        TenantContextVO context = resolveContext(userId, user.getRole(), null, null);
        return inTransaction(() -> openLocal(
                user,
                context,
                clientInstanceId,
                userAgent,
                clientIp));
    }

    private AuthSessionIssue openLocal(
            UserAccountSnapshotVO user,
            TenantContextVO context,
            String clientInstanceId,
            String userAgent,
            String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime absoluteExpiresAt = now.plus(properties.getRefreshAbsoluteTtl());
        String sessionId = UUID.randomUUID().toString();
        String tokenId = UUID.randomUUID().toString();
        String refreshToken = randomToken();
        String userAgentHash = hashSignal(normalizeUserAgent(userAgent));
        String ipNetworkHash = hashSignal(ipNetwork(clientIp));

        AuthRefreshSession session = new AuthRefreshSession();
        session.setSessionId(sessionId);
        session.setUserId(user.getUserId());
        session.setClientInstanceId(normalizeClientInstance(clientInstanceId));
        applyContext(session, context);
        session.setStatus(ACTIVE);
        session.setAbsoluteExpiresAt(absoluteExpiresAt);
        session.setCurrentTokenId(tokenId);
        session.setUserAgentHash(userAgentHash);
        session.setInitialIpNetworkHash(ipNetworkHash);
        session.setLastIpNetworkHash(ipNetworkHash);
        session.setIpChangeCount(0);
        session.setUserAgentChangeCount(0);
        session.setStepUpRequired(0);
        session.setVersion(0);
        sessionMapper.insert(session);

        tokenMapper.insert(newRefreshToken(
                tokenId,
                sessionId,
                refreshToken,
                now,
                absoluteExpiresAt,
                userAgentHash,
                ipNetworkHash));

        AuthResultDTO result = buildAuthResult(
                user,
                sessionId,
                context,
                session.getSecurityVersion());
        return new AuthSessionIssue(
                result,
                refreshToken,
                properties.getRefreshAbsoluteTtl(),
                sessionId);
    }

    @Override
    public AuthSessionIssue refresh(String refreshToken,
                                    String clientInstanceId,
                                    String userAgent,
                                    String clientIp) {
        return prepareRefresh(
                refreshToken,
                clientInstanceId,
                userAgent,
                clientIp,
                false,
                null,
                null);
    }

    @Override
    public AuthSessionIssue refreshAfterStepUp(String refreshToken,
                                               String target,
                                               String code,
                                               String clientInstanceId,
                                               String userAgent,
                                               String clientIp) {
        return prepareRefresh(
                refreshToken,
                clientInstanceId,
                userAgent,
                clientIp,
                true,
                target,
                code);
    }

    private AuthSessionIssue prepareRefresh(
            String refreshToken,
            String clientInstanceId,
            String userAgent,
            String clientIp,
            boolean stepUpVerified,
            String stepUpTarget,
            String stepUpCode) {
        if (!StringUtils.hasText(refreshToken)) {
            throw rejected(401, "REFRESH_TOKEN_MISSING", "登录会话不存在，请重新登录");
        }
        String tokenHash = hashToken(refreshToken);
        AuthRefreshToken tokenSnapshot = tokenMapper.selectByHash(tokenHash);
        if (tokenSnapshot == null) {
            throw rejected(401, "REFRESH_TOKEN_INVALID", "登录会话无效，请重新登录");
        }
        AuthRefreshSession sessionSnapshot =
                sessionMapper.selectById(tokenSnapshot.getSessionId());
        if (sessionSnapshot == null) {
            throw rejected(401, "REFRESH_SESSION_MISSING", "登录会话无效，请重新登录");
        }

        UserAccountSnapshotVO user = null;
        TenantContextVO context = null;
        LocalDateTime now = LocalDateTime.now();
        boolean activeCandidate = ACTIVE.equals(tokenSnapshot.getStatus())
                && ACTIVE.equals(sessionSnapshot.getStatus())
                && now.isBefore(sessionSnapshot.getAbsoluteExpiresAt())
                && now.isBefore(tokenSnapshot.getExpiresAt());
        if (activeCandidate) {
            user = findUser(sessionSnapshot.getUserId());
            if (user != null && Integer.valueOf(1).equals(user.getStatus())) {
                boolean ipChanged = !constantTimeEquals(
                        sessionSnapshot.getLastIpNetworkHash(),
                        hashSignal(ipNetwork(clientIp)));
                boolean userAgentChanged = !constantTimeEquals(
                        sessionSnapshot.getUserAgentHash(),
                        hashSignal(normalizeUserAgent(userAgent)));
                String effectiveClientInstance = StringUtils.hasText(clientInstanceId)
                        ? normalizeClientInstance(clientInstanceId)
                        : sessionSnapshot.getClientInstanceId();
                boolean clientInstanceChanged =
                        !sessionSnapshot.getClientInstanceId()
                                .equals(effectiveClientInstance);
                int changedSignals = (ipChanged ? 1 : 0)
                        + (userAgentChanged ? 1 : 0)
                        + (clientInstanceChanged ? 1 : 0);

                boolean stepUpRequired =
                        Integer.valueOf(1)
                                .equals(sessionSnapshot.getStepUpRequired());
                if (stepUpRequired && stepUpVerified) {
                    verifyRefreshStepUp(
                            user,
                            stepUpTarget,
                            stepUpCode,
                            clientIp);
                }

                boolean contextNeeded =
                        (stepUpRequired && stepUpVerified)
                                || (!stepUpRequired
                                && !stepUpVerified
                                && changedSignals < 2);
                if (contextNeeded) {
                    try {
                        context = resolveContext(
                                user.getUserId(),
                                user.getRole(),
                                sessionSnapshot.getContextType(),
                                sessionSnapshot.getTenantId());
                    } catch (ApiStatusException exception) {
                        if (exception.getHttpStatus() == 401
                                || exception.getHttpStatus() == 403
                                || exception.getHttpStatus() == 404) {
                            revokePreparedFamily(
                                    sessionSnapshot,
                                    "TENANT_CONTEXT_INVALID");
                            throw rejected(
                                    401,
                                    "TENANT_CONTEXT_INVALID",
                                    "当前租户上下文已失效，请重新登录");
                        }
                        throw exception;
                    }
                }
            }
        }

        UserAccountSnapshotVO preparedUser = user;
        TenantContextVO preparedContext = context;
        Integer expectedVersion = sessionSnapshot.getVersion();
        return inTransactionNoRollbackOnRefresh(() -> refreshLocal(
                tokenHash,
                clientInstanceId,
                userAgent,
                clientIp,
                stepUpVerified,
                preparedUser,
                preparedContext,
                expectedVersion));
    }

    private AuthSessionIssue refreshLocal(
            String tokenHash,
            String clientInstanceId,
            String userAgent,
            String clientIp,
            boolean stepUpVerified,
            UserAccountSnapshotVO preparedUser,
            TenantContextVO preparedContext,
            Integer expectedSessionVersion) {
        LocalDateTime now = LocalDateTime.now();
        AuthRefreshToken currentToken =
                tokenMapper.selectByHashForUpdate(tokenHash);
        if (currentToken == null) {
            throw rejected(401, "REFRESH_TOKEN_INVALID", "登录会话无效，请重新登录");
        }
        AuthRefreshSession session = sessionMapper.selectForUpdate(currentToken.getSessionId());
        if (session == null) {
            throw rejected(401, "REFRESH_SESSION_MISSING", "登录会话无效，请重新登录");
        }

        if (!ACTIVE.equals(currentToken.getStatus())) {
            tokenMapper.markReplayed(currentToken.getTokenId(), now);
            revokeLockedFamily(session.getSessionId(), "REFRESH_TOKEN_REPLAY", now);
            throw rejected(401, "REFRESH_TOKEN_REPLAY", "检测到旧 Refresh Token 重放，当前登录会话已撤销");
        }
        if (!ACTIVE.equals(session.getStatus())) {
            tokenMapper.revokeActiveForSession(session.getSessionId(), now);
            throw rejected(401, "REFRESH_SESSION_REVOKED", "登录会话已撤销，请重新登录");
        }
        if (!now.isBefore(session.getAbsoluteExpiresAt()) || !now.isBefore(currentToken.getExpiresAt())) {
            revokeLockedFamily(session.getSessionId(), "REFRESH_SESSION_EXPIRED", now);
            throw rejected(401, "REFRESH_SESSION_EXPIRED", "登录会话已超过7天绝对有效期，请重新验证身份");
        }

        if (!Objects.equals(expectedSessionVersion, session.getVersion())) {
            throw rejected(
                    409,
                    "REFRESH_SESSION_CHANGED",
                    "登录会话已变化，请重新发起刷新");
        }
        UserAccountSnapshotVO user = preparedUser;
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            revokeLockedFamily(session.getSessionId(), "ACCOUNT_UNAVAILABLE", now);
            throw rejected(401, "ACCOUNT_UNAVAILABLE", "账号已停用或删除，请重新验证身份");
        }
        String currentUserAgentHash = hashSignal(normalizeUserAgent(userAgent));
        String currentIpNetworkHash = hashSignal(ipNetwork(clientIp));
        String effectiveClientInstance = StringUtils.hasText(clientInstanceId)
                ? normalizeClientInstance(clientInstanceId) : session.getClientInstanceId();
        boolean ipChanged = !constantTimeEquals(session.getLastIpNetworkHash(), currentIpNetworkHash);
        boolean userAgentChanged = !constantTimeEquals(session.getUserAgentHash(), currentUserAgentHash);
        boolean clientInstanceChanged = !session.getClientInstanceId().equals(effectiveClientInstance);

        int changedSignals = (ipChanged ? 1 : 0)
                + (userAgentChanged ? 1 : 0)
                + (clientInstanceChanged ? 1 : 0);
        if (Integer.valueOf(1).equals(session.getStepUpRequired())) {
            if (!stepUpVerified) {
                throw rejected(
                        403,
                        "REFRESH_STEP_UP_REQUIRED",
                        "登录环境变化较大，请使用验证码重新验证身份");
            }
            recordRisk(session.getSessionId(), "STEP_UP_COMPLETED", null, null);
        } else {
            if (stepUpVerified) {
                throw rejected(
                        409,
                        "REFRESH_STEP_UP_NOT_REQUIRED",
                        "当前登录会话不需要环境复核");
            }
            recordRiskChanges(session, ipChanged, userAgentChanged, clientInstanceChanged,
                    currentIpNetworkHash, currentUserAgentHash, effectiveClientInstance);
            if (changedSignals >= 2) {
                if (sessionMapper.markStepUpRequired(
                        session.getSessionId(), session.getVersion()) != 1) {
                    throw new IllegalStateException("Refresh Session 风险状态更新发生并发冲突");
                }
                recordRisk(session.getSessionId(), "STEP_UP_REQUIRED", null, null);
                throw rejected(
                        403,
                        "REFRESH_STEP_UP_REQUIRED",
                        "登录环境变化较大，请使用验证码重新验证身份");
            }
        }

        TenantContextVO context = preparedContext;
        if (context == null) {
            throw new IllegalStateException("Refresh租户上下文准备缺失");
        }
        String nextTokenId = UUID.randomUUID().toString();
        String nextRefreshToken = randomToken();
        long currentSecurityVersion = session.getSecurityVersion() == null
                ? 0L : session.getSecurityVersion();
        long nextSecurityVersion = stepUpVerified
                ? currentSecurityVersion + 1L
                : currentSecurityVersion;

        if (tokenMapper.markRotated(currentToken.getTokenId(), nextTokenId, now) != 1) {
            revokeLockedFamily(session.getSessionId(), "REFRESH_ROTATION_CONFLICT", now);
            throw rejected(401, "REFRESH_ROTATION_CONFLICT", "Refresh Token 已被使用，当前登录会话已撤销");
        }
        tokenMapper.insert(newRefreshToken(
                nextTokenId,
                session.getSessionId(),
                nextRefreshToken,
                now,
                session.getAbsoluteExpiresAt(),
                currentUserAgentHash,
                currentIpNetworkHash));
        int rotated = sessionMapper.rotate(
                session.getSessionId(),
                session.getVersion(),
                nextTokenId,
                context.getContextType(),
                parseNullableId(context.getTenantId()),
                context.getTenantCode(),
                context.getTenantRole(),
                context.getContextVersion(),
                context.getMemberContextVersion(),
                nextSecurityVersion,
                authoritiesJson(context),
                effectiveClientInstance,
                currentUserAgentHash,
                currentIpNetworkHash,
                ipChanged,
                userAgentChanged);
        if (rotated != 1) {
            throw new IllegalStateException("Refresh Session 旋转发生并发冲突");
        }

        return new AuthSessionIssue(
                buildAuthResult(
                        user,
                        session.getSessionId(),
                        context,
                        nextSecurityVersion),
                nextRefreshToken,
                Duration.between(now, session.getAbsoluteExpiresAt()),
                session.getSessionId());
    }

    @Override
    public AuthSessionIssue replace(String currentRefreshToken,
                                    String expectedSessionId,
                                    AuthResultDTO nextIdentity,
                                    String clientInstanceId,
                                    String userAgent,
                                    String clientIp) {
        if (!StringUtils.hasText(currentRefreshToken)) {
            throw ApiStatusException.conflict("当前登录会话不存在，不能执行账号切换");
        }
        String tokenHash = hashToken(currentRefreshToken);
        AuthRefreshToken tokenSnapshot = tokenMapper.selectByHash(tokenHash);
        AuthRefreshSession sessionSnapshot = tokenSnapshot == null
                ? null
                : sessionMapper.selectById(tokenSnapshot.getSessionId());
        UserAccountSnapshotVO nextUser = null;
        TenantContextVO nextContext = null;
        LocalDateTime now = LocalDateTime.now();
        boolean activeCandidate = tokenSnapshot != null
                && sessionSnapshot != null
                && StringUtils.hasText(expectedSessionId)
                && expectedSessionId.equals(tokenSnapshot.getSessionId())
                && ACTIVE.equals(tokenSnapshot.getStatus())
                && ACTIVE.equals(sessionSnapshot.getStatus())
                && now.isBefore(sessionSnapshot.getAbsoluteExpiresAt())
                && now.isBefore(tokenSnapshot.getExpiresAt());
        if (activeCandidate) {
            Long nextUserId = parsePositiveId(
                    nextIdentity == null ? null : nextIdentity.getUserId(),
                    "用户ID");
            nextUser = requireActiveUser(nextUserId);
            nextContext = resolveContext(
                    nextUserId,
                    nextUser.getRole(),
                    null,
                    null);
        }
        UserAccountSnapshotVO preparedUser = nextUser;
        TenantContextVO preparedContext = nextContext;
        Integer expectedVersion = sessionSnapshot == null
                ? null : sessionSnapshot.getVersion();
        return inTransactionNoRollbackOnRefresh(() -> replaceLocal(
                tokenHash,
                expectedSessionId,
                preparedUser,
                preparedContext,
                expectedVersion,
                clientInstanceId,
                userAgent,
                clientIp));
    }

    private AuthSessionIssue replaceLocal(
            String tokenHash,
            String expectedSessionId,
            UserAccountSnapshotVO nextUser,
            TenantContextVO nextContext,
            Integer expectedSessionVersion,
            String clientInstanceId,
            String userAgent,
            String clientIp) {
        AuthRefreshToken currentToken =
                tokenMapper.selectByHashForUpdate(tokenHash);
        if (currentToken == null) {
            throw ApiStatusException.conflict("当前登录会话已失效，请重新登录");
        }
        AuthRefreshSession currentSession =
                sessionMapper.selectForUpdate(currentToken.getSessionId());
        if (currentSession == null) {
            throw rejected(
                    401,
                    "REFRESH_SESSION_MISSING",
                    "当前登录会话已失效，请重新登录");
        }
        if (!StringUtils.hasText(expectedSessionId)
                || !expectedSessionId.equals(currentToken.getSessionId())) {
            throw rejected(
                    409,
                    "ACCOUNT_SWITCH_SESSION_MISMATCH",
                    "当前 Access JWT 与 Refresh Session 不一致，请刷新页面后重试");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!ACTIVE.equals(currentToken.getStatus())) {
            tokenMapper.markReplayed(currentToken.getTokenId(), now);
            revokeLockedFamily(currentToken.getSessionId(), "REFRESH_TOKEN_REPLAY", now);
            throw rejected(
                    401,
                    "REFRESH_TOKEN_REPLAY",
                    "检测到旧 Refresh Token 重放，当前登录会话已撤销");
        }
        if (!ACTIVE.equals(currentSession.getStatus())) {
            tokenMapper.revokeActiveForSession(currentToken.getSessionId(), now);
            throw rejected(
                    401,
                    "REFRESH_SESSION_REVOKED",
                    "当前登录会话已撤销，请重新登录");
        }
        if (!now.isBefore(currentSession.getAbsoluteExpiresAt())
                || !now.isBefore(currentToken.getExpiresAt())) {
            revokeLockedFamily(currentToken.getSessionId(), "REFRESH_SESSION_EXPIRED", now);
            throw rejected(
                    401,
                    "REFRESH_SESSION_EXPIRED",
                    "当前登录会话已超过7天绝对有效期，请重新验证身份");
        }
        if (!Objects.equals(
                expectedSessionVersion,
                currentSession.getVersion())) {
            throw rejected(
                    409,
                    "ACCOUNT_SWITCH_SESSION_CHANGED",
                    "当前登录会话已变化，请刷新页面后重试");
        }
        if (nextUser == null || nextContext == null) {
            throw new IllegalStateException("账号切换会话准备缺失");
        }
        revokeLockedFamily(currentToken.getSessionId(), "ACCOUNT_SWITCHED", now);
        return openLocal(
                nextUser,
                nextContext,
                clientInstanceId,
                userAgent,
                clientIp);
    }

    @Override
    public AuthResultDTO switchContext(String sessionId,
                                       Long userId,
                                       Integer role,
                                       TenantContextVO context) {
        if (!StringUtils.hasText(sessionId)) {
            throw ApiStatusException.forbidden("当前 Access JWT 不支持上下文切换，请重新登录");
        }
        UserAccountSnapshotVO user = requireActiveUser(userId);
        if (!user.getRole().equals(role)) {
            throw ApiStatusException.forbidden("用户角色已变化，请重新登录");
        }
        return inTransaction(() ->
                switchContextLocal(sessionId, userId, context, user));
    }

    private AuthResultDTO switchContextLocal(
            String sessionId,
            Long userId,
            TenantContextVO context,
            UserAccountSnapshotVO user) {
        AuthRefreshSession currentSession = sessionMapper.selectForUpdate(sessionId);
        if (currentSession == null
                || !userId.equals(currentSession.getUserId())
                || !ACTIVE.equals(currentSession.getStatus())
                || !LocalDateTime.now().isBefore(currentSession.getAbsoluteExpiresAt())) {
            throw ApiStatusException.conflict("登录会话已变化，请刷新后重试");
        }
        int updated = sessionMapper.updateContext(
                sessionId,
                userId,
                context.getContextType(),
                parseNullableId(context.getTenantId()),
                context.getTenantCode(),
                context.getTenantRole(),
                context.getContextVersion(),
                context.getMemberContextVersion(),
                authoritiesJson(context));
        if (updated != 1) {
            throw ApiStatusException.conflict("登录会话已变化，请刷新后重试");
        }
        return buildAuthResult(
                user,
                sessionId,
                context,
                currentSession.getSecurityVersion());
    }

    @Override
    public void logout(String refreshToken, String reason) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        inTransaction(() -> {
            AuthRefreshToken token =
                    tokenMapper.selectByHashForUpdate(hashToken(refreshToken));
            if (token != null) {
                revokeLockedFamily(
                        token.getSessionId(),
                        safeReason(reason),
                        LocalDateTime.now());
            }
            return null;
        });
    }

    @Override
    public void revokeSession(String sessionId, String reason) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        inTransaction(() -> {
            revokeLockedFamily(
                    sessionId,
                    safeReason(reason),
                    LocalDateTime.now());
            return null;
        });
    }

    @Override
    public void revokeAllForUser(Long userId, String reason) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID无效");
        }
        inTransaction(() -> {
            LocalDateTime now = LocalDateTime.now();
            tokenMapper.revokeActiveForUser(userId, now);
            sessionMapper.revokeAllForUser(
                    userId,
                    safeReason(reason),
                    now);
            return null;
        });
    }

    @Override
    public SessionValidationVO validate(String sessionId, Long userId, String tokenId) {
        SessionValidationVO result = new SessionValidationVO();
        result.setSessionId(sessionId);
        result.setUserId(userId == null ? null : String.valueOf(userId));
        if (!StringUtils.hasText(sessionId)
                || !StringUtils.hasText(tokenId)
                || userId == null
                || userId <= 0) {
            result.setActive(false);
            result.setStatus("INVALID");
            result.setReason("SESSION_IDENTITY_INVALID");
            return result;
        }
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(REVOKED_JTI_PREFIX + tokenId))) {
                result.setActive(false);
                result.setStatus("REVOKED");
                result.setReason("ACCESS_TOKEN_REVOKED");
                return result;
            }
        } catch (RuntimeException exception) {
            throw ApiStatusException.serviceUnavailable("Access Token 撤销校验依赖暂时不可用");
        }
        AuthRefreshSession session = sessionMapper.selectById(sessionId);
        LocalDateTime now = LocalDateTime.now();
        if (session == null || !userId.equals(session.getUserId())) {
            result.setActive(false);
            result.setStatus("INVALID");
            result.setReason("SESSION_NOT_FOUND");
            return result;
        }
        result.setContextType(session.getContextType());
        result.setTenantId(session.getTenantId() == null ? null : String.valueOf(session.getTenantId()));
        result.setContextVersion(session.getTenantContextVersion());
        result.setMemberContextVersion(session.getMemberContextVersion());
        result.setSecurityVersion(
                session.getSecurityVersion() == null ? 0L : session.getSecurityVersion());
        if (!ACTIVE.equals(session.getStatus())) {
            result.setActive(false);
            result.setStatus(session.getStatus());
            result.setReason(session.getRevokeReason());
            return result;
        }
        if (Integer.valueOf(1).equals(session.getStepUpRequired())) {
            result.setActive(false);
            result.setStatus("STEP_UP_REQUIRED");
            result.setReason("REFRESH_STEP_UP_REQUIRED");
            return result;
        }
        if (!now.isBefore(session.getAbsoluteExpiresAt())) {
            inTransaction(() -> {
                revokeLockedFamily(
                        sessionId,
                        "REFRESH_SESSION_EXPIRED",
                        now);
                return null;
            });
            result.setActive(false);
            result.setStatus("EXPIRED");
            result.setReason("REFRESH_SESSION_EXPIRED");
            return result;
        }
        UserAccountSnapshotVO user = findUser(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            inTransaction(() -> {
                revokeLockedFamily(
                        sessionId,
                        "ACCOUNT_UNAVAILABLE",
                        now);
                return null;
            });
            result.setActive(false);
            result.setStatus("REVOKED");
            result.setReason("ACCOUNT_UNAVAILABLE");
            return result;
        }
        result.setActive(true);
        result.setStatus(ACTIVE);
        return result;
    }

    @Override
    public void revokeAccessToken(String tokenId, long expiresAtEpochMillis, String reason) {
        if (!StringUtils.hasText(tokenId)) {
            throw new IllegalArgumentException("Access Token jti 不能为空");
        }
        long ttlMillis = expiresAtEpochMillis - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    REVOKED_JTI_PREFIX + tokenId,
                    safeReason(reason),
                    ttlMillis,
                    TimeUnit.MILLISECONDS);
        } catch (RuntimeException exception) {
            throw ApiStatusException.serviceUnavailable("Access Token 撤销存储暂时不可用");
        }
    }

    private TenantContextVO resolveContext(Long userId,
                                           Integer role,
                                           String contextType,
                                           Long tenantId) {
        TenantContextResolveRequest request = new TenantContextResolveRequest();
        request.setUserId(String.valueOf(userId));
        request.setGlobalRole(role);
        request.setContextType(contextType);
        request.setTenantId(tenantId == null ? null : String.valueOf(tenantId));
        ApiResponse<TenantContextVO> response;
        try {
            response = remoteCallTemplate.execute(status ->
                    tenantContextClient.resolve(internalToken, request));
        } catch (FeignException exception) {
            LOGGER.warn(
                    "tenant context resolve failed: status={}, exception={}, contextType={}, tenantIdPresent={}",
                    exception.status(),
                    exception.getClass().getSimpleName(),
                    contextType,
                    tenantId != null);
            if (exception.status() == 400) {
                throw new IllegalArgumentException("租户上下文请求无效");
            }
            if (exception.status() == 401) {
                throw new ApiStatusException(401, 401, "租户上下文已变化");
            }
            if (exception.status() == 403) {
                throw ApiStatusException.forbidden("无权使用目标租户上下文");
            }
            if (exception.status() == 404) {
                throw ApiStatusException.notFound("目标租户或用户不存在");
            }
            throw ApiStatusException.serviceUnavailable("租户上下文服务暂时不可用");
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "tenant context resolve failed before HTTP response: exception={}, contextType={}, tenantIdPresent={}",
                    exception.getClass().getSimpleName(),
                    contextType,
                    tenantId != null);
            throw ApiStatusException.serviceUnavailable("租户上下文服务暂时不可用");
        }
        if (response == null || response.getCode() != 200 || response.getData() == null) {
            LOGGER.warn(
                    "tenant context resolve returned invalid response: responsePresent={}, code={}, dataPresent={}, contextType={}, tenantIdPresent={}",
                    response != null,
                    response == null ? null : response.getCode(),
                    response != null && response.getData() != null,
                    contextType,
                    tenantId != null);
            throw ApiStatusException.serviceUnavailable("租户上下文服务返回无效结果");
        }
        return response.getData();
    }

    private UserAccountSnapshotVO requireActiveUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID无效");
        }
        UserAccountSnapshotVO user = findUser(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw ApiStatusException.forbidden("用户当前不可用");
        }
        return user;
    }

    private AuthResultDTO buildAuthResult(UserAccountSnapshotVO user,
                                          String sessionId,
                                          TenantContextVO context,
                                          Long securityVersion) {
        AuthResultDTO result = new AuthResultDTO();
        result.setUserId(String.valueOf(user.getUserId()));
        result.setUsername(user.getUsername());
        result.setRole(user.getRole());
        result.setNickname(user.getNickname());
        result.setAvatar(user.getAvatar());
        result.setAccountState("ACTIVE");
        result.setContext(context);
        result.setToken(jwtUtils.generateAccessToken(
                user.getUserId(),
                user.getUsername(),
                user.getRole(),
                sessionId,
                context.getContextType(),
                context.getTenantId(),
                context.getTenantCode(),
                context.getTenantRole(),
                context.getContextVersion(),
                context.getMemberContextVersion(),
                securityVersion,
                context.getAuthorities()));
        return result;
    }

    private AuthRefreshToken newRefreshToken(String tokenId,
                                             String sessionId,
                                             String rawToken,
                                             LocalDateTime issuedAt,
                                             LocalDateTime expiresAt,
                                             String userAgentHash,
                                             String ipNetworkHash) {
        AuthRefreshToken token = new AuthRefreshToken();
        token.setTokenId(tokenId);
        token.setSessionId(sessionId);
        token.setTokenHash(hashToken(rawToken));
        token.setStatus(ACTIVE);
        token.setIssuedAt(issuedAt);
        token.setExpiresAt(expiresAt);
        token.setUserAgentHash(userAgentHash);
        token.setIpNetworkHash(ipNetworkHash);
        return token;
    }

    private void applyContext(AuthRefreshSession session, TenantContextVO context) {
        session.setContextType(context.getContextType());
        session.setTenantId(parseNullableId(context.getTenantId()));
        session.setTenantCode(context.getTenantCode());
        session.setTenantRole(context.getTenantRole());
        session.setTenantContextVersion(context.getContextVersion());
        session.setMemberContextVersion(context.getMemberContextVersion());
        session.setSecurityVersion(0L);
        session.setAuthoritiesJson(authoritiesJson(context));
    }

    private String authoritiesJson(TenantContextVO context) {
        try {
            return objectMapper.writeValueAsString(
                    context.getAuthorities() == null ? Collections.emptyList() : context.getAuthorities());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("平台权限序列化失败");
        }
    }

    private void revokeLockedFamily(String sessionId, String reason, LocalDateTime now) {
        tokenMapper.revokeActiveForSession(sessionId, now);
        sessionMapper.revokeFamily(sessionId, reason, now);
    }

    private void revokePreparedFamily(
            AuthRefreshSession prepared,
            String reason) {
        inTransaction(() -> {
            AuthRefreshSession current =
                    sessionMapper.selectForUpdate(prepared.getSessionId());
            if (current != null
                    && ACTIVE.equals(current.getStatus())
                    && Objects.equals(
                    prepared.getVersion(),
                    current.getVersion())) {
                revokeLockedFamily(
                        current.getSessionId(),
                        reason,
                        LocalDateTime.now());
            }
            return null;
        });
    }

    private <T> T inTransaction(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    private <T> T inTransactionNoRollbackOnRefresh(
            Supplier<T> action) {
        AtomicReference<RefreshSessionException> failure =
                new AtomicReference<>();
        T result = transactionTemplate.execute(status -> {
            try {
                return action.get();
            } catch (RefreshSessionException exception) {
                failure.set(exception);
                return null;
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return result;
    }

    private void recordRiskChanges(AuthRefreshSession session,
                                   boolean ipChanged,
                                   boolean userAgentChanged,
                                   boolean clientInstanceChanged,
                                   String currentIpNetworkHash,
                                   String currentUserAgentHash,
                                   String currentClientInstance) {
        if (ipChanged) {
            recordRisk(session.getSessionId(), "IP_NETWORK_CHANGED",
                    session.getLastIpNetworkHash(), currentIpNetworkHash);
        }
        if (userAgentChanged) {
            recordRisk(session.getSessionId(), "USER_AGENT_CHANGED",
                    session.getUserAgentHash(), currentUserAgentHash);
        }
        if (clientInstanceChanged) {
            recordRisk(session.getSessionId(), "CLIENT_INSTANCE_CHANGED",
                    hashSignal(session.getClientInstanceId()), hashSignal(currentClientInstance));
        }
    }

    private void recordRisk(String sessionId, String type, String previous, String current) {
        AuthRefreshRiskEvent event = new AuthRefreshRiskEvent();
        event.setSessionId(sessionId);
        event.setEventType(type);
        event.setPreviousSignalHash(previous);
        event.setCurrentSignalHash(current);
        riskEventMapper.insert(event);
    }

    private void verifyRefreshStepUp(UserAccountSnapshotVO user,
                                     String target,
                                     String code,
                                     String clientIp) {
        if (!StringUtils.hasText(target) || !StringUtils.hasText(code)) {
            throw rejected(
                    403,
                    "REFRESH_STEP_UP_INVALID",
                    "请提供当前账号的验证码完成登录环境复核");
        }
        String normalizedTarget = target.trim();
        boolean targetOwned = normalizedTarget.equals(user.getPhone())
                || (StringUtils.hasText(user.getEmail())
                && normalizedTarget.equalsIgnoreCase(user.getEmail()));
        if (!targetOwned) {
            throw rejected(
                    403,
                    "REFRESH_STEP_UP_INVALID",
                    "验证码目标不属于当前账号");
        }
        try {
            verificationCodeService.consumeCode(
                    normalizedTarget,
                    "step_up",
                    code,
                    clientIp);
        } catch (IllegalArgumentException exception) {
            throw rejected(
                    403,
                    "REFRESH_STEP_UP_INVALID",
                    exception.getMessage());
        }
    }

    private RefreshSessionException rejected(int status, String code, String message) {
        return new RefreshSessionException(status, code, message);
    }

    private UserAccountSnapshotVO findUser(Long userId) {
        try {
            return remoteCallTemplate.execute(status ->
                    userAccountGateway.findById(userId));
        } catch (RuntimeException exception) {
            throw userAccountGateway.mapFailure("账号读取", exception);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        return sha256(rawToken);
    }

    private String hashSignal(String value) {
        return sha256(value == null ? "unknown" : value);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private boolean constantTimeEquals(String first, String second) {
        if (first == null || second == null) {
            return first == null && second == null;
        }
        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.UTF_8),
                second.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeClientInstance(String value) {
        if (!StringUtils.hasText(value)) {
            return "server-" + UUID.randomUUID();
        }
        String normalized = value.trim();
        return normalized.length() > 128 ? normalized.substring(0, 128) : normalized;
    }

    private String normalizeUserAgent(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        String normalized = value.trim();
        return normalized.length() > 512 ? normalized.substring(0, 512) : normalized;
    }

    private String ipNetwork(String value) {
        String ip = StringUtils.hasText(value) ? value.trim() : "";
        try {
            if (ip.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
                byte[] address = InetAddress.getByName(ip).getAddress();
                return (address[0] & 0xff) + "." + (address[1] & 0xff) + "." + (address[2] & 0xff) + ".0/24";
            }
            if (ip.contains(":") && ip.matches("^[0-9A-Fa-f:]+$")) {
                byte[] address = InetAddress.getByName(ip).getAddress();
                StringBuilder builder = new StringBuilder();
                for (int index = 0; index < 8; index += 2) {
                    if (builder.length() > 0) {
                        builder.append(':');
                    }
                    builder.append(String.format("%02x%02x", address[index] & 0xff, address[index + 1] & 0xff));
                }
                return builder.append("::/64").toString();
            }
        } catch (Exception ignored) {
            // 无法解析的代理地址只作为 unknown 风险信号，不参与网络访问。
        }
        return "unknown";
    }

    private Long parsePositiveId(String value, String label) {
        if (!StringUtils.hasText(value) || !value.matches("^[1-9]\\d*$")) {
            throw new IllegalArgumentException(label + "无效");
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + "超出64位整数范围");
        }
    }

    private Long parseNullableId(String value) {
        return StringUtils.hasText(value) ? parsePositiveId(value, "租户ID") : null;
    }

    private String safeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "SECURITY_EVENT";
        }
        String normalized = reason.trim();
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }
}
