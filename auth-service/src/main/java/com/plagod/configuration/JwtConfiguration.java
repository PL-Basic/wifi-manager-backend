package com.plagod.configuration;

import com.plagod.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfiguration {

    @Bean
    public JwtUtils jwtUtils(
            @Value("${wifi.jwt.secret}") String secret,
            @Value("${wifi.jwt.expiration-millis}") long expirationMillis,
            @Value("${wifi.jwt.issuer}") String issuer,
            @Value("${wifi.jwt.algorithm}") String algorithm) {
        if (expirationMillis != 900000L) {
            throw new IllegalStateException("P-2 要求 Access JWT 固定为15分钟");
        }
        if (!"HS256".equals(algorithm)) {
            throw new IllegalStateException("P-2 要求 JWT algorithm 固定为HS256");
        }
        return new JwtUtils(secret, expirationMillis, issuer);
    }
}
