package com.plagod.utils;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilsTest {

    private static final String TEST_SECRET = "test-only-jwt-secret-32-bytes-minimum-value";

    @Test
    void generatedTokenCanBeParsedWithSameConfiguredKey() {
        JwtUtils jwtUtils = new JwtUtils(TEST_SECRET, 60_000L);

        String token = jwtUtils.generateToken(7L, "admin", 0);
        Claims claims = jwtUtils.parseToken(token);

        assertEquals(7L, JwtUtils.getUserId(claims));
        assertEquals("admin", claims.get("username", String.class));
        assertEquals(0, Integer.valueOf(String.valueOf(claims.get("role"))));
    }

    @Test
    void differentConfiguredKeyCannotParseToken() {
        JwtUtils issuer = new JwtUtils(TEST_SECRET, 60_000L);
        JwtUtils verifier = new JwtUtils("another-test-only-jwt-secret-32-bytes-minimum", 60_000L);

        String token = issuer.generateToken(7L, "admin", 0);

        assertThrows(Exception.class, () -> verifier.parseToken(token));
    }

    @Test
    void weakOrPlaceholderConfigurationFailsClosed() {
        assertThrows(IllegalStateException.class, () -> new JwtUtils("too-short", 60_000L));
        assertThrows(IllegalStateException.class, () -> new JwtUtils("CHANGE_ME_RANDOM_JWT_SECRET_AT_LEAST_32_BYTES", 60_000L));
        assertThrows(IllegalStateException.class, () -> new JwtUtils(TEST_SECRET, 0L));
    }
}
