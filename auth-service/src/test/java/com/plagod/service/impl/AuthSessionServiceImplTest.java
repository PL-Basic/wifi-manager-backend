package com.plagod.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.client.TenantContextClient;
import com.plagod.configuration.AuthSessionProperties;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.auth.AuthResultDTO;
import com.plagod.entity.auth.AuthRefreshSession;
import com.plagod.entity.auth.AuthRefreshToken;
import com.plagod.exception.RefreshSessionException;
import com.plagod.mapper.AuthRefreshRiskEventMapper;
import com.plagod.mapper.AuthRefreshSessionMapper;
import com.plagod.mapper.AuthRefreshTokenMapper;
import com.plagod.service.UserAccountGateway;
import com.plagod.service.VerificationCodeService;
import com.plagod.transaction.TestTransactionManager;
import com.plagod.utils.JwtUtils;
import com.plagod.vo.AuthSessionIssue;
import com.plagod.vo.auth.SessionValidationVO;
import com.plagod.vo.tenant.TenantContextVO;
import com.plagod.vo.user.UserAccountSnapshotVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private UserAccountGateway userAccountGateway;
    private TenantContextClient tenantContextClient;
    private JwtUtils jwtUtils;
    private StringRedisTemplate redisTemplate;
    private VerificationCodeService verificationCodeService;
    private TestTransactionManager transactionManager;
    private AuthSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(AuthRefreshSessionMapper.class);
        tokenMapper = mock(AuthRefreshTokenMapper.class);
        riskEventMapper = mock(AuthRefreshRiskEventMapper.class);
        userAccountGateway = mock(UserAccountGateway.class);
        tenantContextClient = mock(TenantContextClient.class);
        jwtUtils = mock(JwtUtils.class);
        redisTemplate = mock(StringRedisTemplate.class);
        verificationCodeService = mock(VerificationCodeService.class);
        transactionManager = new TestTransactionManager();

        AuthSessionProperties properties = new AuthSessionProperties();
        properties.setRefreshAbsoluteTtl(Duration.ofDays(7));
        properties.setOperationTokenTtl(Duration.ofMinutes(5));

        service = new AuthSessionServiceImpl(
                sessionMapper,
                tokenMapper,
                riskEventMapper,
                userAccountGateway,
                tenantContextClient,
                jwtUtils,
                new ObjectMapper(),
                properties,
                redisTemplate,
                verificationCodeService,
                transactionManager,
                "test-internal-token-value");
    }

    @Test
    void openPersistsOnlyRefreshTokenHash() {
        UserAccountSnapshotVO user = user(7L, 2);
        TenantContextVO context = tenantContext();
        when(userAccountGateway.findById(7L)).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager
                    .isActualTransactionActive());
            return user;
        });
        when(tenantContextClient.resolve(anyString(), any()))
                .thenAnswer(invocation -> {
                    assertFalse(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return ApiResponse.success(context);
                });
        when(sessionMapper.insert(any())).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager
                    .isActualTransactionActive());
            return 1;
        });
        when(tokenMapper.insert(any())).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager
                    .isActualTransactionActive());
            return 1;
        });
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
        verify(tenantContextClient).resolve(
                eq("test-internal-token-value"),
                any());
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
        when(tokenMapper.selectByHash(anyString())).thenReturn(token);
        when(sessionMapper.selectById("session-id")).thenReturn(session);

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
        when(tokenMapper.selectByHash(anyString())).thenReturn(token);
        when(sessionMapper.selectById("session-id")).thenReturn(session);
        when(userAccountGateway.findById(7L)).thenReturn(user(7L, 2));
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

        UserAccountSnapshotVO user = user(7L, 2);
        user.setEmail("alice@example.com");
        TenantContextVO context = tenantContext();
        when(tokenMapper.selectByHashForUpdate(anyString())).thenReturn(token);
        when(sessionMapper.selectForUpdate("session-id")).thenReturn(session);
        when(tokenMapper.selectByHash(anyString())).thenReturn(token);
        when(sessionMapper.selectById("session-id")).thenReturn(session);
        when(userAccountGateway.findById(7L)).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager
                    .isActualTransactionActive());
            return user;
        });
        when(tenantContextClient.resolve(anyString(), any()))
                .thenAnswer(invocation -> {
                    assertFalse(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return ApiResponse.success(context);
                });
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager
                    .isActualTransactionActive());
            return null;
        }).when(verificationCodeService).consumeCode(
                anyString(),
                anyString(),
                anyString(),
                anyString());
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
        when(tokenMapper.selectByHash(anyString())).thenReturn(token);
        when(sessionMapper.selectById("session-id")).thenReturn(session);

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

    @Test
    void validAccountReplaceSuspendsRemoteCallsBeforeLocalWriteTransaction() {
        LocalDateTime now = LocalDateTime.now();
        AuthRefreshToken token = activeToken(now);
        AuthRefreshSession session = activeSession(now);
        UserAccountSnapshotVO nextUser = user(8L, 2);
        TenantContextVO context = tenantContext();
        when(tokenMapper.selectByHash(anyString())).thenReturn(token);
        when(sessionMapper.selectById("session-id")).thenReturn(session);
        when(userAccountGateway.findById(8L)).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager
                    .isActualTransactionActive());
            return nextUser;
        });
        when(tenantContextClient.resolve(anyString(), any()))
                .thenAnswer(invocation -> {
                    assertFalse(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return ApiResponse.success(context);
                });
        when(tokenMapper.selectByHashForUpdate(anyString()))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return token;
                });
        when(sessionMapper.selectForUpdate("session-id"))
                .thenReturn(session);
        when(sessionMapper.insert(any())).thenReturn(1);
        when(tokenMapper.insert(any())).thenReturn(1);
        when(jwtUtils.generateAccessToken(
                anyLong(), anyString(), anyInt(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyLong(), anyLong(),
                anyLong(), anyList())).thenReturn("replacement-access-token");
        AuthResultDTO nextIdentity = new AuthResultDTO();
        nextIdentity.setUserId("8");

        AuthSessionIssue issue =
                new TransactionTemplate(transactionManager).execute(status ->
                        service.replace(
                                "active-refresh-token",
                                "session-id",
                                nextIdentity,
                                "replacement-client",
                                "replacement-agent",
                                "192.168.1.24"));

        assertNotNull(issue);
        assertEquals("replacement-access-token",
                issue.getAuthResult().getToken());
        verify(tokenMapper).revokeActiveForSession(
                eq("session-id"),
                any(LocalDateTime.class));
        verify(sessionMapper).revokeFamily(
                eq("session-id"),
                eq("ACCOUNT_SWITCHED"),
                any(LocalDateTime.class));
        verify(sessionMapper).insert(any(AuthRefreshSession.class));
        verify(tokenMapper).insert(any(AuthRefreshToken.class));
    }

    @Test
    void accountReplaceRejectsVersionChangedAfterRemotePreparation() {
        LocalDateTime now = LocalDateTime.now();
        AuthRefreshToken token = activeToken(now);
        AuthRefreshSession snapshot = activeSession(now);
        snapshot.setVersion(4);
        AuthRefreshSession changed = activeSession(now);
        changed.setVersion(5);
        when(tokenMapper.selectByHash(anyString())).thenReturn(token);
        when(sessionMapper.selectById("session-id")).thenReturn(snapshot);
        when(userAccountGateway.findById(8L)).thenReturn(user(8L, 2));
        when(tenantContextClient.resolve(anyString(), any()))
                .thenReturn(ApiResponse.success(tenantContext()));
        when(tokenMapper.selectByHashForUpdate(anyString())).thenReturn(token);
        when(sessionMapper.selectForUpdate("session-id")).thenReturn(changed);
        AuthResultDTO nextIdentity = new AuthResultDTO();
        nextIdentity.setUserId("8");

        RefreshSessionException exception = assertThrows(
                RefreshSessionException.class,
                () -> service.replace(
                        "active-refresh-token",
                        "session-id",
                        nextIdentity,
                        "replacement-client",
                        "replacement-agent",
                        "192.168.1.24"));

        assertEquals(409, exception.getHttpStatus());
        assertEquals("ACCOUNT_SWITCH_SESSION_CHANGED",
                exception.getCode());
        verify(tokenMapper, never()).revokeActiveForSession(
                anyString(),
                any());
        verify(sessionMapper, never()).revokeFamily(
                anyString(),
                anyString(),
                any());
        verify(sessionMapper, never()).insert(any());
        verify(tokenMapper, never()).insert(any());
    }

    @Test
    void repeatedUserRevokeKeepsActiveOnlyNaturalIdempotency() {
        service.revokeAllForUser(7L, "ACCOUNT_DISABLED");
        service.revokeAllForUser(7L, "ACCOUNT_DISABLED");

        verify(tokenMapper, times(2)).revokeActiveForUser(
                eq(7L),
                any(LocalDateTime.class));
        verify(sessionMapper, times(2)).revokeAllForUser(
                eq(7L),
                eq("ACCOUNT_DISABLED"),
                any(LocalDateTime.class));
    }

    @Test
    void revokedAccessTokenIsRejectedBeforeSessionLookup() {
        when(redisTemplate.hasKey(
                "auth:access-jti:revoked:old-access-jti"))
                .thenReturn(true);

        SessionValidationVO result = service.validate(
                "session-id",
                7L,
                "old-access-jti");

        assertFalse(result.getActive());
        assertEquals("REVOKED", result.getStatus());
        assertEquals("ACCESS_TOKEN_REVOKED", result.getReason());
        verifyNoInteractions(sessionMapper, userAccountGateway);
    }

    @Test
    void revokedSessionCannotValidateAnotherAccessToken() {
        AuthRefreshSession session = activeSession(LocalDateTime.now());
        session.setStatus("REVOKED");
        session.setRevokeReason("ACCOUNT_SECURITY_CHANGED");
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(sessionMapper.selectById("session-id")).thenReturn(session);

        SessionValidationVO result = service.validate(
                "session-id",
                7L,
                "newer-access-jti");

        assertFalse(result.getActive());
        assertEquals("REVOKED", result.getStatus());
        assertEquals("ACCOUNT_SECURITY_CHANGED", result.getReason());
        verifyNoInteractions(userAccountGateway);
    }

    @Test
    void validationReturnsCurrentPlatformTenantAndSecurityVersions() {
        AuthRefreshSession session = activeSession(LocalDateTime.now());
        session.setContextType("PLATFORM_TENANT");
        session.setTenantId(19L);
        session.setTenantContextVersion(8L);
        session.setMemberContextVersion(null);
        session.setSecurityVersion(6L);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(sessionMapper.selectById("session-id")).thenReturn(session);
        when(userAccountGateway.findById(7L)).thenReturn(user(7L, 0));

        SessionValidationVO result = service.validate(
                "session-id",
                7L,
                "current-access-jti");

        assertTrue(result.getActive());
        assertEquals("PLATFORM_TENANT", result.getContextType());
        assertEquals("19", result.getTenantId());
        assertEquals(8L, result.getContextVersion());
        assertNull(result.getMemberContextVersion());
        assertEquals(6L, result.getSecurityVersion());
    }

    @Test
    void platformTenantSwitchSignsWithoutTenantMemberIdentity() {
        LocalDateTime now = LocalDateTime.now();
        AuthRefreshSession session = activeSession(now);
        session.setSecurityVersion(6L);
        UserAccountSnapshotVO user = user(7L, 0);
        TenantContextVO context = platformTenantContext();
        when(userAccountGateway.findById(7L)).thenReturn(user);
        when(sessionMapper.selectForUpdate("session-id")).thenReturn(session);
        when(sessionMapper.updateContext(
                eq("session-id"),
                eq(7L),
                eq("PLATFORM_TENANT"),
                eq(19L),
                eq("managed-tenant"),
                isNull(),
                eq(8L),
                isNull(),
                eq("[\"TENANT_MANAGE\"]")))
                .thenReturn(1);
        when(jwtUtils.generateAccessToken(
                eq(7L),
                eq("alice"),
                eq(0),
                eq("session-id"),
                eq("PLATFORM_TENANT"),
                eq("19"),
                eq("managed-tenant"),
                isNull(),
                eq(8L),
                isNull(),
                eq(6L),
                eq(Collections.singletonList("TENANT_MANAGE"))))
                .thenReturn("platform-tenant-access-token");

        AuthResultDTO result = service.switchContext(
                "session-id",
                7L,
                0,
                context);

        assertEquals("platform-tenant-access-token", result.getToken());
        verify(sessionMapper).updateContext(
                "session-id",
                7L,
                "PLATFORM_TENANT",
                19L,
                "managed-tenant",
                null,
                8L,
                null,
                "[\"TENANT_MANAGE\"]");
        verify(jwtUtils).generateAccessToken(
                7L,
                "alice",
                0,
                "session-id",
                "PLATFORM_TENANT",
                "19",
                "managed-tenant",
                null,
                8L,
                null,
                6L,
                Collections.singletonList("TENANT_MANAGE"));
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
        session.setVersion(0);
        return session;
    }

    private UserAccountSnapshotVO user(Long userId, Integer role) {
        UserAccountSnapshotVO user = new UserAccountSnapshotVO();
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

    private TenantContextVO platformTenantContext() {
        TenantContextVO context = new TenantContextVO();
        context.setContextType("PLATFORM_TENANT");
        context.setTenantId("19");
        context.setTenantCode("managed-tenant");
        context.setTenantName("代管租户");
        context.setTenantRole(null);
        context.setContextVersion(8L);
        context.setMemberContextVersion(null);
        context.setWritable(true);
        context.setAuthorities(
                Collections.singletonList("TENANT_MANAGE"));
        return context;
    }
}
