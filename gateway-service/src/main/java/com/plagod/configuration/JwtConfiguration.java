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
            @Value("${wifi.jwt.expiration-millis}") long expirationMillis) {
        return new JwtUtils(secret, expirationMillis);
    }
}
