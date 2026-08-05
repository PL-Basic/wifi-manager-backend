package com.plagod.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.client.TenantContextClient;
import com.plagod.configuration.AuthSessionProperties;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.auth.AuthResultDTO;
import com.plagod.entity.auth.AuthRefreshSession;
import com.plagod.entity.auth.AuthRefreshToken;
import com.plagod.entity.user.User;
import com.plagod.exception.RefreshSessionException;
import com.plagod.mapper.AuthRefreshRiskEventMapper;
import com.plagod.mapper.AuthRefreshSessionMapper;
import com.plagod.mapper.AuthRefreshTokenMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.service.VerificationCodeService;
import com.plagod.utils.JwtUtils;
import com.plagod.vo.AuthSessionIssue;
import com.plagod.vo.tenant.TenantContextVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthSessionServiceImplTest {

    private AuthRefreshSessionMapper sessionMapper;
    private AuthRefreshTokenMapper tokenMapper;
    private AuthRefreshRiskEventMapper riskEventMapper;
    private UserMapper userMapper;
    private TenantContextClient tenantContextClient;
    private JwtUtils jwtUtils;
    private StringRedisTemplate redisTemplate;
    private VerificationCodeService verificationCodeService;
    private AuthSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(AuthRefreshSessionMapper.class);
        tokenMapper = mock(AuthRefreshTokenMapper.class);
        riskEventMapper = mock(AuthRefreshRiskEventMapper.class);
        userMapper = mock(UserMapper.class);
        tenantContextClient = mock(TenantContextClient.class);
        jwtUtils = mock(JwtUtils.class);
        redisTemplate = mock(StringRedisTemplate.class);
        verificationCodeService = mock(VerificationCodeService.class);

        AuthSessionProperties properties = new AuthSessionProperties();
        properties.setRefreshAbsoluteTtl(Duration.ofDays(7));
        properties.setOperationTokenTtl(Duration.ofMinutes(5));

        service = new AuthSessionServiceImpl(
                sessionMapper,
                tokenMapper,
                riskEventMapper,
                userMapper,
                tenantContextClient,
                jwtUtils,
                new ObjectMapper(),
                properties,
                redisTemplate,
                verificationCodeService,
                "test-internal-token-value");
    }

    @Test
    void openPersistsOnlyRefreshTokenHash() {
        User user = user(7L, 2);
        TenantContextVO context = tenantContext();
        when(userMapper.selectById(7L)).thenReturn(user);
        when(tenantContextClient.resolve(anyString(), any()))
                .thenReturn(ApiResponse.success(context));
        when(jwtUtils.generateAccessToken(
                anyLong(), anyString(), anyInt(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyLong(), anyLong(),
                anyLong(), anyList()))
                .thenReturn("access-token");

        AuthResultDTO identity = new AuthResultDTO();
        identity.setUserId("7");
        AuthSessionIssue issue = service.open(
                identity,
                "client-instance",
                "test-agent",
                "192.168.1.23");

        ArgumentCaptor<AuthRefreshToken> tokenCaptor =
                ArgumentCaptor.forClass(AuthRefreshToken.class);
        verify(tokenMapper).insert(tokenCaptor.capture());
        AuthRefreshToken persisted = tokenCaptor.getValue();

        assertNotNull(issue.getRefreshToken());
        assertEquals(43, issue.getRefreshToken().length());
        assertNotEquals(issue.getRefreshToken(), persisted.getTokenHash());
        assertEquals(64, persisted.getTokenHash().length());
        assertEquals("access-token", issue.getAuthResult().getToken());
        assertEquals(Duration.ofDays(7), issue.getCookieMaxAge());
    }

    @Test
    void replayedRotatedTokenRevokesWholeFamily() {
        LocalDateTime now = LocalDateTime.now();
        AuthRefreshToken token = new AuthRefreshToken();
        token.setTokenId("old-token-id");
        token.setSessionId("session-id");
        token.setStatus("ROTATED");
        token.setExpiresAt(now.plusDays(1));

        AuthRefreshSession session = new AuthRefreshSession();
        session.setSessionId("session-id");
        session.setUserId(7L);
        session.setStatus("ACTIVE");
        session.setAbsoluteExpiresAt(now.plusDays(1));

        when(tokenMapper.selectByHashForUpdate(anyString())).thenReturn(token);
        when(sessionMapper.selectForUpdate("session-id")).thenReturn(session);

        RefreshSessionException exception = assertThrows(
                RefreshSessionException.class,
                () -> service.refresh(
                        "old-refresh-token",
                        "client-instance",
                        "test-agent",
                        "192.168.1.23"));

        assertEquals("REFRESH_TOKEN_REPLAY", exception.getCode());
        verify(tokenMapper).markReplayed(eq("old-token-id"), any(LocalDateTime.class));
        verify(tokenMapper).revokeActiveForSession(eq("session-id"), any(LocalDateTime.class));
        verify(sessionMapper).revokeFamily(
                eq("session-id"),
                eq("REFRESH_TOKEN_REPLAY"),
                any(LocalDateTime.class));
    }

    @Test
    void twoChangedSignalsRequireRecoverableStepUpWithoutRevokingFamily() {
        LocalDateTime now = LocalDateTime.now();
        AuthRefreshToken token = activeToken(now);
        AuthRefreshSession session = activeSession(now);
        session.setClientInstanceId("old-client");
        session.setUserAgentHash("old-agent-hash");
        session.setLastIpNetworkHash("old-network-hash");
        session.setStepUpRequired(0);
        session.setVersion(3);

        when(tokenMapper.selectByHashForUpdate(anyString())).thenReturn(token);
        when(sessionMapper.selectForUpdate("session-id")).thenReturn(session);
        when(userMapper.selectById(7L)).thenReturn(user(7L, 2));
        when(sessionMapper.markStepUpRequired("session-id", 3)).thenReturn(1);

        RefreshSessionException exception = assertThrows(
                RefreshSessionException.class,
                () -> service.refresh(
                        "active-refresh-token",
                        "new-client",
                        "new-agent",
                        "10.0.0.9"));

        assertEquals(403, exception.getHttpStatus());
        assertEquals("REFRESH_STEP_UP_REQUIRED", exception.getCode());
        verify(sessionMapper).markStepUpRequired("session-id", 3);
        verify(tokenMapper, never()).markRotated(anyString(), anyString(), any());
        verify(tokenMapper, never()).revokeActiveForSession(anyString(), any());
        verify(verificationCodeService, never()).consumeCode(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void verifiedStepUpRotatesAndStoresCurrentClientInstance() {
        LocalDateTime now = LocalDateTime.now();
        AuthRefreshToken token = activeToken(now);
        AuthRefreshSession session = activeSession(now);
        session.setClientInstanceId("old-client");
        session.setUserAgentHash("old-agent-hash");
        session.setLastIpNetworkHash("old-network-hash");
        session.setStepUpRequired(1);
        session.setVersion(4);

        User user = user(7L, 2);
        user.setEmail("alice@example.com");
        TenantContextVO context = tenantContext();
        when(tokenMapper.selectByHashForUpdate(anyString())).thenReturn(token);
        when(sessionMapper.selectForUpdate("session-id")).thenReturn(session);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(tenantContextClient.resolve(anyString(), any()))
                .thenReturn(ApiResponse.success(context));
        when(tokenMapper.markRotated(eq("token-id"), anyString(), any())).thenReturn(1);
        when(sessionMapper.rotate(
                eq("session-id"),
                eq(4),
                anyString(),
                eq("TENANT"),
                eq(11L),
                eq("default-tenant"),
                eq("MEMBER"),
                eq(3L),
                eq(5L),
                eq(1L),
                eq("[]"),
                eq("new-client"),
                anyString(),
                anyString(),
                eq(true),
                eq(true))).thenReturn(1);
        when(jwtUtils.generateAccessToken(
                anyLong(), anyString(), anyInt(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyLong(), anyLong(),
                anyLong(), anyList()))
                .thenReturn("renewed-access-token");

        AuthSessionIssue issue = service.refreshAfterStepUp(
                "active-refresh-token",
                "alice@example.com",
                "ABC123",
                "new-client",
                "new-agent",
                "10.0.0.9");

        assertEquals("session-id", issue.getSessionId());
        assertEquals("renewed-access-token", issue.getAuthResult().getToken());
        verify(verificationCodeService).consumeCode(
                "alice@example.com",
                "step_up",
                "ABC123",
                "10.0.0.9");
        verify(sessionMapper).rotate(
                eq("session-id"),
                eq(4),
                anyString(),
                eq("TENANT"),
                eq(11L),
                eq("default-tenant"),
                eq("MEMBER"),
                eq(3L),
                eq(5L),
                eq(1L),
                eq("[]"),
                eq("new-client"),
                anyString(),
                anyString(),
                eq(true),
                eq(true));
    }

    @Test
    void accountSwitchRejectsRefreshCookieFromAnotherAccessSession() {
        LocalDateTime now = LocalDateTime.now();
        AuthRefreshToken token = activeToken(now);
        AuthRefreshSession session = activeSession(now);
        when(tokenMapper.selectByHashForUpdate(anyString())).thenReturn(token);
        when(sessionMapper.selectForUpdate("session-id")).thenReturn(session);

        AuthResultDTO nextIdentity = new AuthResultDTO();
        nextIdentity.setUserId("8");

        RefreshSessionException exception = assertThrows(
                RefreshSessionException.class,
                () -> service.replace(
                        "active-refresh-token",
                        "different-session-id",
                        nextIdentity,
                        "client-instance",
                        "test-agent",
                        "192.168.1.23"));

        assertEquals(409, exception.getHttpStatus());
        assertEquals("ACCOUNT_SWITCH_SESSION_MISMATCH", exception.getCode());
        verify(tokenMapper, never()).revokeActiveForSession(anyString(), any());
        verify(sessionMapper, never()).revokeFamily(anyString(), anyString(), any());
    }

    private AuthRefreshToken activeToken(LocalDateTime now) {
        AuthRefreshToken token = new AuthRefreshToken();
        token.setTokenId("token-id");
        token.setSessionId("session-id");
        token.setStatus("ACTIVE");
        token.setExpiresAt(now.plusDays(1));
        return token;
    }

    private AuthRefreshSession activeSession(LocalDateTime now) {
        AuthRefreshSession session = new AuthRefreshSession();
        session.setSessionId("session-id");
        session.setUserId(7L);
        session.setStatus("ACTIVE");
        session.setAbsoluteExpiresAt(now.plusDays(1));
        session.setContextType("TENANT");
        session.setTenantId(11L);
        session.setSecurityVersion(0L);
        return session;
    }

    private User user(Long userId, Integer role) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername("alice");
        user.setNickname("Alice");
        user.setRole(role);
        user.setStatus(1);
        return user;
    }

    private TenantContextVO tenantContext() {
        TenantContextVO context = new TenantContextVO();
        context.setContextType("TENANT");
        context.setTenantId("11");
        context.setTenantCode("default-tenant");
        context.setTenantName("默认租户");
        context.setTenantRole("MEMBER");
        context.setContextVersion(3L);
        context.setMemberContextVersion(5L);
        context.setWritable(true);
        context.setAuthorities(Collections.emptyList());
        return context;
    }
}
