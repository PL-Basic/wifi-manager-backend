package com.plagod.service;

import com.plagod.client.AuthSessionWebClient;
import com.plagod.client.TenantContextWebClient;
import com.plagod.dto.tenant.TenantContextValidationRequest;
import com.plagod.utils.JwtUtils;
import com.plagod.vo.auth.SessionValidationVO;
import com.plagod.vo.tenant.TenantContextVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayIdentityValidationServiceTest {

    private AuthSessionWebClient authSessionClient;
    private TenantContextWebClient tenantContextClient;
    private JwtUtils jwtUtils;
    private GatewayIdentityValidationService service;

    @BeforeEach
    void setUp() {
        authSessionClient = mock(AuthSessionWebClient.class);
        tenantContextClient = mock(TenantContextWebClient.class);
        jwtUtils = mock(JwtUtils.class);
        service = new GatewayIdentityValidationService(
                authSessionClient,
                tenantContextClient,
                jwtUtils,
                "1970-01-01T00:00:00Z",
                5000L,
                128);
        when(jwtUtils.hasExpectedIssuer(any(Claims.class))).thenReturn(true);
    }

    @Test
    void switchedSessionRejectsOldAccessJwtBeforeTenantLookup() {
        Claims claims = tenantClaims(3L, 5L);
        SessionValidationVO currentSession = activeSession(4L, 5L);
        when(authSessionClient.validate("session-id", 7L, "access-jti"))
                .thenReturn(Mono.just(currentSession));

        GatewayValidationException exception = assertThrows(
                GatewayValidationException.class,
                () -> service.validate(
                        claims,
                        7L,
                        "alice",
                        2,
                        HttpMethod.GET).block());

        assertEquals(401, exception.getHttpStatus());
        verify(tenantContextClient, never()).validate(any());
    }

    @Test
    void securityStepUpRejectsAccessJwtFromPreviousSecurityVersion() {
        Claims claims = tenantClaims(3L, 5L);
        SessionValidationVO currentSession = activeSession(3L, 5L);
        currentSession.setSecurityVersion(1L);
        when(authSessionClient.validate("session-id", 7L, "access-jti"))
                .thenReturn(Mono.just(currentSession));

        GatewayValidationException exception = assertThrows(
                GatewayValidationException.class,
                () -> service.validate(
                        claims,
                        7L,
                        "alice",
                        2,
                        HttpMethod.GET).block());

        assertEquals(401, exception.getHttpStatus());
        verify(tenantContextClient, never()).validate(any());
    }

    @Test
    void writeValidationDependencyFailureRemainsServiceUnavailable() {
        Claims claims = tenantClaims(3L, 5L);
        when(authSessionClient.validate("session-id", 7L, "access-jti"))
                .thenReturn(Mono.just(activeSession(3L, 5L)));
        when(tenantContextClient.validate(any()))
                .thenReturn(Mono.error(new GatewayValidationException(
                        503,
                        503,
                        "租户上下文校验服务暂时不可用")));

        GatewayValidationException exception = assertThrows(
                GatewayValidationException.class,
                () -> service.validate(
                        claims,
                        7L,
                        "alice",
                        2,
                        HttpMethod.POST).block());

        assertEquals(503, exception.getHttpStatus());
        ArgumentCaptor<TenantContextValidationRequest> requestCaptor =
                ArgumentCaptor.forClass(TenantContextValidationRequest.class);
        verify(tenantContextClient).validate(requestCaptor.capture());
        assertTrue(requestCaptor.getValue().getWriteRequest());
    }

    private Claims tenantClaims(Long contextVersion, Long memberContextVersion) {
        Claims claims = Jwts.claims()
                .setSubject("7")
                .setIssuer("wifi-manager")
                .setAudience(JwtUtils.ACCESS_AUDIENCE)
                .setId("access-jti");
        claims.put("username", "alice");
        claims.put("role", 2);
        claims.put("sid", "session-id");
        claims.put("contextType", "TENANT");
        claims.put("tenantId", "11");
        claims.put("tenantCode", "default-tenant");
        claims.put("tenantRole", "MEMBER");
        claims.put("contextVersion", contextVersion);
        claims.put("memberContextVersion", memberContextVersion);
        claims.put("sessionSecurityVersion", 0L);
        claims.put("authorities", Collections.emptyList());
        return claims;
    }

    private SessionValidationVO activeSession(Long contextVersion,
                                              Long memberContextVersion) {
        SessionValidationVO session = new SessionValidationVO();
        session.setActive(true);
        session.setStatus("ACTIVE");
        session.setSessionId("session-id");
        session.setUserId("7");
        session.setContextType("TENANT");
        session.setTenantId("11");
        session.setContextVersion(contextVersion);
        session.setMemberContextVersion(memberContextVersion);
        session.setSecurityVersion(0L);
        return session;
    }
}
