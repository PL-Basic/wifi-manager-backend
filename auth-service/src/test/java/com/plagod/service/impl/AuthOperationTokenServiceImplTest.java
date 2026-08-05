package com.plagod.service.impl;

import com.plagod.configuration.AuthSessionProperties;
import com.plagod.dto.auth.OperationTokenConsumeRequest;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.UserMapper;
import com.plagod.service.AuthSessionService;
import com.plagod.service.VerificationCodeService;
import com.plagod.utils.JwtUtils;
import com.plagod.vo.auth.OperationTokenConsumptionVO;
import com.plagod.vo.auth.SessionValidationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthOperationTokenServiceImplTest {

    private static final String SECRET =
            "operation-token-test-secret-value-32-bytes-minimum";

    private ValueOperations<String, String> valueOperations;
    private AuthSessionService authSessionService;
    private JwtUtils jwtUtils;
    private AuthOperationTokenServiceImpl service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        UserMapper userMapper = mock(UserMapper.class);
        VerificationCodeService verificationCodeService =
                mock(VerificationCodeService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        authSessionService = mock(AuthSessionService.class);
        jwtUtils = new JwtUtils(SECRET, 900_000L, "wifi-manager");
        AuthSessionProperties properties = new AuthSessionProperties();
        properties.setRefreshAbsoluteTtl(Duration.ofDays(7));
        properties.setOperationTokenTtl(Duration.ofMinutes(5));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service = new AuthOperationTokenServiceImpl(
                userMapper,
                verificationCodeService,
                jwtUtils,
                redisTemplate,
                properties,
                authSessionService);
    }

    @Test
    void firstConsumptionBindsPurposeAndBusinessKey() {
        OperationTokenConsumeRequest request =
                consumeRequest("DELETE_ACCOUNT", "delete-user:7");
        when(authSessionService.validate(anyString(), eq(7L), anyString()))
                .thenReturn(activeSession());
        when(valueOperations.setIfAbsent(
                anyString(),
                eq("DELETE_ACCOUNT:delete-user:7"),
                anyLong(),
                eq(TimeUnit.MILLISECONDS))).thenReturn(true);

        OperationTokenConsumptionVO result = service.consume(request);

        assertEquals("7", result.getUserId());
        assertEquals("DELETE_ACCOUNT", result.getPurpose());
        assertEquals("delete-user:7", result.getBusinessKey());
    }

    @Test
    void repeatedJtiConsumptionIsRejected() {
        OperationTokenConsumeRequest request =
                consumeRequest("PRIVILEGE_CHANGE", "role-change:7");
        when(authSessionService.validate(anyString(), eq(7L), anyString()))
                .thenReturn(activeSession());
        when(valueOperations.setIfAbsent(
                anyString(),
                anyString(),
                anyLong(),
                eq(TimeUnit.MILLISECONDS))).thenReturn(false);

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.consume(request));

        assertEquals(409, exception.getHttpStatus());
    }

    @Test
    void previousSecurityVersionCannotConsumeOperationToken() {
        OperationTokenConsumeRequest request =
                consumeRequest("PAYMENT", "payment:7");
        SessionValidationVO currentSession = activeSession();
        currentSession.setSecurityVersion(1L);
        when(authSessionService.validate(anyString(), eq(7L), anyString()))
                .thenReturn(currentSession);

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.consume(request));

        assertEquals(403, exception.getHttpStatus());
        verify(valueOperations, never()).setIfAbsent(
                anyString(),
                anyString(),
                anyLong(),
                eq(TimeUnit.MILLISECONDS));
    }

    private OperationTokenConsumeRequest consumeRequest(String purpose,
                                                        String businessKey) {
        OperationTokenConsumeRequest request = new OperationTokenConsumeRequest();
        request.setToken(jwtUtils.generateOperationToken(
                7L,
                "session-id",
                purpose,
                Duration.ofMinutes(5).toMillis()));
        request.setPurpose(purpose);
        request.setBusinessKey(businessKey);
        return request;
    }

    private SessionValidationVO activeSession() {
        SessionValidationVO session = new SessionValidationVO();
        session.setActive(true);
        session.setStatus("ACTIVE");
        session.setSessionId("session-id");
        session.setUserId("7");
        session.setSecurityVersion(0L);
        return session;
    }
}
