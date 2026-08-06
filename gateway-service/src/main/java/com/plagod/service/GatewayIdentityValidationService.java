package com.plagod.service;

import com.plagod.client.AuthSessionWebClient;
import com.plagod.client.TenantContextWebClient;
import com.plagod.dto.tenant.TenantContextResolveRequest;
import com.plagod.dto.tenant.TenantContextValidationRequest;
import com.plagod.utils.JwtUtils;
import com.plagod.vo.auth.SessionValidationVO;
import com.plagod.vo.tenant.TenantContextVO;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GatewayIdentityValidationService {

    private final AuthSessionWebClient authSessionClient;
    private final TenantContextWebClient tenantContextClient;
    private final JwtUtils jwtUtils;
    private final Instant legacyAcceptUntil;
    private final long readCacheMillis;
    private final int maximumCacheEntries;
    private final Map<String, CachedContext> readCache = new ConcurrentHashMap<>();

    public GatewayIdentityValidationService(
            AuthSessionWebClient authSessionClient,
            TenantContextWebClient tenantContextClient,
            JwtUtils jwtUtils,
            @Value("${wifi.jwt.legacy-accept-until:1970-01-01T00:00:00Z}") String legacyAcceptUntil,
            @Value("${wifi.context-validation.read-cache-millis:5000}") long readCacheMillis,
            @Value("${wifi.context-validation.maximum-cache-entries:4096}") int maximumCacheEntries) {
        this.authSessionClient = authSessionClient;
        this.tenantContextClient = tenantContextClient;
        this.jwtUtils = jwtUtils;
        this.legacyAcceptUntil = parseInstant(legacyAcceptUntil);
        this.readCacheMillis = Math.min(5000L, Math.max(0L, readCacheMillis));
        this.maximumCacheEntries = Math.max(128, maximumCacheEntries);
    }

    public Mono<GatewayIdentityContext> validate(Claims claims,
                                                 Long userId,
                                                 String username,
                                                 Integer role,
                                                 HttpMethod method) {
        String sessionId = JwtUtils.getSessionId(claims);
        String tokenId = claims.getId();
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(tokenId)) {
            return validateLegacy(claims, userId, username, role, method);
        }
        if (!jwtUtils.hasExpectedIssuer(claims)
                || !JwtUtils.ACCESS_AUDIENCE.equals(claims.getAudience())) {
            return Mono.error(new GatewayValidationException(
                    401, 401, "Access JWT issuer 或 audience 无效"));
        }

        return authSessionClient.validate(sessionId, userId, tokenId)
                .flatMap(session -> {
                    requireActiveSession(session);
                    requireCurrentSessionContext(claims, session);
                    TenantContextValidationRequest request =
                            validationRequest(claims, userId, role, !isSafeMethod(method));
                    return validateTenantWithReadFallback(request, tokenId, isSafeMethod(method))
                            .map(context -> identity(
                                    userId,
                                    username,
                                    role,
                                    sessionId,
                                    tokenId,
                                    false,
                                    context));
                });
    }

    private Mono<GatewayIdentityContext> validateLegacy(Claims claims,
                                                        Long userId,
                                                        String username,
                                                        Integer role,
                                                        HttpMethod method) {
        Instant now = Instant.now();
        Instant issuedAt = claims.getIssuedAt() == null
                ? Instant.EPOCH : claims.getIssuedAt().toInstant();
        if (!now.isBefore(legacyAcceptUntil) || !issuedAt.isBefore(legacyAcceptUntil)) {
            return Mono.error(new GatewayValidationException(
                    401, 401, "旧 Access JWT 兼容窗口已经结束，请重新登录"));
        }

        TenantContextResolveRequest request = new TenantContextResolveRequest();
        request.setUserId(String.valueOf(userId));
        request.setGlobalRole(role);
        return tenantContextClient.resolve(request)
                .flatMap(context -> {
                    if (isSafeMethod(method)) {
                        return Mono.just(context);
                    }
                    return tenantContextClient.validate(
                            validationRequest(context, userId, role, true, true));
                })
                .map(context -> identity(
                    userId,
                    username,
                    role,
                    null,
                    null,
                    true,
                    context));
    }

    private Mono<TenantContextVO> validateTenantWithReadFallback(
            TenantContextValidationRequest request,
            String cacheKey,
            boolean safeMethod) {
        Mono<TenantContextVO> source = tenantContextClient.validate(request)
                .doOnNext(context -> {
                    if (safeMethod && readCacheMillis > 0) {
                        putCache(cacheKey, context);
                    }
                });
        if (!safeMethod || readCacheMillis <= 0) {
            return source;
        }
        return source.onErrorResume(GatewayValidationException.class, exception -> {
            if (exception.getHttpStatus() != 503) {
                return Mono.error(exception);
            }
            CachedContext cached = readCache.get(cacheKey);
            if (cached != null && System.currentTimeMillis() - cached.verifiedAt <= readCacheMillis) {
                return Mono.just(cached.context);
            }
            return Mono.error(exception);
        });
    }

    private TenantContextValidationRequest validationRequest(
            Claims claims,
            Long userId,
            Integer role,
            boolean writeRequest) {
        TenantContextValidationRequest request = new TenantContextValidationRequest();
        request.setUserId(String.valueOf(userId));
        request.setGlobalRole(role);
        request.setContextType(JwtUtils.getContextType(claims));
        request.setTenantId(stringClaim(claims, "tenantId"));
        request.setTenantCode(stringClaim(claims, "tenantCode"));
        request.setTenantRole(stringClaim(claims, "tenantRole"));
        request.setContextVersion(JwtUtils.getLongClaim(claims, "contextVersion"));
        request.setMemberContextVersion(JwtUtils.getLongClaim(claims, "memberContextVersion"));
        request.setAuthorities(JwtUtils.getAuthorities(claims));
        request.setWriteRequest(writeRequest);
        request.setLegacyToken(false);
        return request;
    }

    private TenantContextValidationRequest validationRequest(
            TenantContextVO context,
            Long userId,
            Integer role,
            boolean writeRequest,
            boolean legacyToken) {
        TenantContextValidationRequest request = new TenantContextValidationRequest();
        request.setUserId(String.valueOf(userId));
        request.setGlobalRole(role);
        request.setContextType(context.getContextType());
        request.setTenantId(context.getTenantId());
        request.setTenantCode(context.getTenantCode());
        request.setTenantRole(context.getTenantRole());
        request.setContextVersion(context.getContextVersion());
        request.setMemberContextVersion(context.getMemberContextVersion());
        request.setAuthorities(context.getAuthorities());
        request.setWriteRequest(writeRequest);
        request.setLegacyToken(legacyToken);
        return request;
    }

    private void requireActiveSession(SessionValidationVO session) {
        if (session == null || !Boolean.TRUE.equals(session.getActive())) {
            String message = session != null && StringUtils.hasText(session.getReason())
                    ? session.getReason() : "登录会话已经失效";
            throw new GatewayValidationException(401, 401, message);
        }
    }

    private void requireCurrentSessionContext(Claims claims, SessionValidationVO session) {
        if (!Objects.equals(JwtUtils.getContextType(claims), session.getContextType())
                || !Objects.equals(stringClaim(claims, "tenantId"), session.getTenantId())
                || !Objects.equals(
                        JwtUtils.getLongClaim(claims, "contextVersion"),
                        session.getContextVersion())
                || !Objects.equals(
                        JwtUtils.getLongClaim(claims, "memberContextVersion"),
                        session.getMemberContextVersion())
                || !Objects.equals(
                        JwtUtils.getLongClaim(claims, "sessionSecurityVersion"),
                        session.getSecurityVersion())) {
            throw new GatewayValidationException(
                    401, 401, "登录会话上下文已经切换，请使用最新 Access JWT");
        }
    }

    private GatewayIdentityContext identity(Long userId,
                                            String username,
                                            Integer role,
                                            String sessionId,
                                            String tokenId,
                                            boolean legacy,
                                            TenantContextVO context) {
        GatewayIdentityContext identity = new GatewayIdentityContext();
        identity.setUserId(userId);
        identity.setUsername(username);
        identity.setRole(role);
        identity.setSessionId(sessionId);
        identity.setTokenId(tokenId);
        identity.setLegacyToken(legacy);
        identity.setTenantContext(context);
        return identity;
    }

    private boolean isSafeMethod(HttpMethod method) {
        return HttpMethod.GET.equals(method)
                || HttpMethod.HEAD.equals(method)
                || HttpMethod.OPTIONS.equals(method);
    }

    private String stringClaim(Claims claims, String name) {
        Object value = claims.get(name);
        return value == null ? null : String.valueOf(value);
    }

    private void putCache(String key, TenantContextVO context) {
        if (readCache.size() >= maximumCacheEntries) {
            long now = System.currentTimeMillis();
            readCache.entrySet().removeIf(entry ->
                    now - entry.getValue().verifiedAt > readCacheMillis);
            if (readCache.size() >= maximumCacheEntries) {
                String firstKey = readCache.keySet().stream().findFirst().orElse(null);
                if (firstKey != null) {
                    readCache.remove(firstKey);
                }
            }
        }
        readCache.put(key, new CachedContext(context, System.currentTimeMillis()));
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException("旧 JWT 兼容截止时间必须是 ISO-8601 UTC 时间");
        }
    }

    private static final class CachedContext {
        private final TenantContextVO context;
        private final long verifiedAt;

        private CachedContext(TenantContextVO context, long verifiedAt) {
            this.context = context;
            this.verifiedAt = verifiedAt;
        }
    }
}
