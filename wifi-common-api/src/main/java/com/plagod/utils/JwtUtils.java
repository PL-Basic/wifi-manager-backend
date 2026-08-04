package com.plagod.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Locale;

/**
 * JWT 工具本身不注册为全局 Spring 组件，只有 Auth 和 Gateway 可以显式创建实例。
 * 调用方在 auth-service（签发）与 gateway-service（解析）注入本类；
 * getUserId(Claims) 保留静态是因为它不依赖任何状态，纯函数。
 */
public class JwtUtils {

    private final long expirationMillis;
    private final Key key;

    public JwtUtils(String secret, long expirationMillis) {
        validateSecret(secret);
        if (expirationMillis <= 0) {
            throw new IllegalStateException("JWT 过期时间必须大于 0");
        }

        this.expirationMillis = expirationMillis;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String username, Integer role) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
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

    public static Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
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
