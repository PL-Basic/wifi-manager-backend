package com.plagod.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * JWT 工具本身不注册为全局 Spring 组件，只有 Auth 和 Gateway 可以显式创建实例。
 * 调用方在 auth-service（签发）与 gateway-service（解析）注入本类；
 * getUserId(Claims) 保留静态是因为它不依赖任何状态，纯函数。
 */
public class JwtUtils {

    public static final String ACCESS_AUDIENCE = "wifi-access";
    public static final String OPERATION_AUDIENCE = "wifi-operation";

    private final long expirationMillis;
    private final Key key;
    private final String issuer;

    public JwtUtils(String secret, long expirationMillis) {
        this(secret, expirationMillis, "wifi-manager");
    }

    public JwtUtils(String secret, long expirationMillis, String issuer) {
        validateSecret(secret);
        if (expirationMillis <= 0) {
            throw new IllegalStateException("JWT 过期时间必须大于 0");
        }
        if (issuer == null || issuer.trim().isEmpty()) {
            throw new IllegalStateException("JWT issuer 不能为空");
        }

        this.expirationMillis = expirationMillis;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer.trim();
    }

    public String generateToken(Long userId, String username, Integer role) {
        return generateAccessToken(
                userId,
                username,
                role,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Collections.emptyList());
    }

    public String generateAccessToken(Long userId,
                                      String username,
                                      Integer role,
                                      String sessionId,
                                      String contextType,
                                      String tenantId,
                                      String tenantCode,
                                      String tenantRole,
                                      Long contextVersion,
                                      Long memberContextVersion,
                                      List<String> authorities) {
        return generateAccessToken(
                userId,
                username,
                role,
                sessionId,
                contextType,
                tenantId,
                tenantCode,
                tenantRole,
                contextVersion,
                memberContextVersion,
                0L,
                authorities);
    }

    public String generateAccessToken(Long userId,
                                      String username,
                                      Integer role,
                                      String sessionId,
                                      String contextType,
                                      String tenantId,
                                      String tenantCode,
                                      String tenantRole,
                                      Long contextVersion,
                                      Long memberContextVersion,
                                      Long sessionSecurityVersion,
                                      List<String> authorities) {
        Date issuedAt = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuer(issuer)
                .setAudience(ACCESS_AUDIENCE)
                .setId(UUID.randomUUID().toString())
                .claim("username", username)
                .claim("role", role)
                .claim("sid", sessionId)
                .claim("contextType", contextType)
                .claim("tenantId", tenantId)
                .claim("tenantCode", tenantCode)
                .claim("tenantRole", tenantRole)
                .claim("contextVersion", contextVersion)
                .claim("memberContextVersion", memberContextVersion)
                .claim(
                        "sessionSecurityVersion",
                        sessionSecurityVersion == null ? 0L : sessionSecurityVersion)
                .claim("authorities", authorities == null ? Collections.emptyList() : new ArrayList<>(authorities))
                .setIssuedAt(issuedAt)
                .setExpiration(new Date(issuedAt.getTime() + expirationMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateOperationToken(Long userId,
                                         String sessionId,
                                         String purpose,
                                         long validityMillis) {
        return generateOperationToken(
                userId,
                sessionId,
                purpose,
                0L,
                validityMillis);
    }

    public String generateOperationToken(Long userId,
                                         String sessionId,
                                         String purpose,
                                         Long sessionSecurityVersion,
                                         long validityMillis) {
        if (validityMillis <= 0) {
            throw new IllegalArgumentException("一次性操作凭证有效期必须大于0");
        }
        if (purpose == null || purpose.trim().isEmpty()) {
            throw new IllegalArgumentException("一次性操作凭证 purpose 不能为空");
        }
        Date issuedAt = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuer(issuer)
                .setAudience(OPERATION_AUDIENCE)
                .setId(UUID.randomUUID().toString())
                .claim("sid", sessionId)
                .claim("purpose", purpose.trim())
                .claim(
                        "sessionSecurityVersion",
                        sessionSecurityVersion == null ? 0L : sessionSecurityVersion)
                .setIssuedAt(issuedAt)
                .setExpiration(new Date(issuedAt.getTime() + validityMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasExpectedIssuer(Claims claims) {
        return claims != null && issuer.equals(claims.getIssuer());
    }

    public static Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public static String getSessionId(Claims claims) {
        return claims.get("sid", String.class);
    }

    public static String getContextType(Claims claims) {
        return claims.get("contextType", String.class);
    }

    public static Integer getRole(Claims claims) {
        Object role = claims.get("role");
        return role == null ? null : Integer.valueOf(String.valueOf(role));
    }

    public static Long getLongClaim(Claims claims, String name) {
        Object value = claims.get(name);
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    public static List<String> getAuthorities(Claims claims) {
        Object value = claims.get("authorities");
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object authority : (List<?>) value) {
            if (authority != null) {
                result.add(String.valueOf(authority));
            }
        }
        return result;
    }

    private void validateSecret(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT 密钥必须由 Nacos wifi-jwt.yml 提供且不少于 32 字节");
        }

        if (!secret.equals(secret.trim())) {
            throw new IllegalStateException("JWT 密钥首尾不能包含空白字符");
        }

        String upperSecret = secret.toUpperCase(Locale.ROOT);
        if (upperSecret.contains("CHANGE_ME") || upperSecret.contains("LOCAL-DEV") || upperSecret.contains("YOURSECRETKEY")) {
            throw new IllegalStateException("JWT 密钥仍是示例值，拒绝启动");
        }
    }
}
