package com.plagod.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * 由 spring 管理；secret / 过期时间从配置项注入，默认值兜底以便老代码 / 单元测试无配置也能跑。
 * 之后接 Nacos 配置中心时只需在配置项里覆盖 jwt.secret，本类无需改动。
 *
 * 调用方在 auth-service（签发）与 gateway-service（解析）注入本类的实例方法；
 * getUserId(Claims) 保留静态是因为它不依赖任何状态，纯函数。
 */
@Component
public class JwtUtils {

    @Value("${jwt.secret:YourSecretKey-AtLeast256Bits-ChangeInProduction!}")
    private String secret;

    @Value("${jwt.expiration-millis:86400000}")
    private long expirationMillis;

    private Key key;

    @PostConstruct
    void init() {
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
}
