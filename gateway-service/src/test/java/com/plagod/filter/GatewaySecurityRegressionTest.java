package com.plagod.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.ratelimit.GatewayRateLimiter;
import com.plagod.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewaySecurityRegressionTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private GatewayRateLimiter gatewayRateLimiter;

    private JwtAuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthGlobalFilter();

        ReflectionTestUtils.setField(filter, "jwtUtils", jwtUtils);
        ReflectionTestUtils.setField(filter, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(filter, "gatewayRateLimiter", gatewayRateLimiter);

        ReflectionTestUtils.setField(filter, "gatewayToken", "test-gateway-token");
        ReflectionTestUtils.setField(filter, "trustProxyHeaders", false);
        ReflectionTestUtils.setField(filter, "authLimit", 3);
        ReflectionTestUtils.setField(filter, "oauthLimit", 3);
        ReflectionTestUtils.setField(filter, "portalLimit", 3);
        ReflectionTestUtils.setField(filter, "locationLimit", 3);
        ReflectionTestUtils.setField(filter, "websocketLimit", 3);
        ReflectionTestUtils.setField(filter, "paymentCallbackLimit", 3);
        ReflectionTestUtils.setField(filter, "commerceWriteLimit", 3);
    }

    @Test
    void anonymousLoginRemovesForgedIdentityAndUsesRemoteAddress() {
        when(gatewayRateLimiter.acquire(eq("auth"), eq("127.0.0.1"), eq(3), eq(Duration.ofMinutes(1))))
                .thenReturn(Mono.just(GatewayRateLimiter.Decision.allowed(60L, false)));

        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.POST, "/auth/login")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 50100))
                .header("X-User-Id", "999")
                .header("X-User-Role", "0")
                .header("X-Gateway-Token", "forged")
                .header("X-Client-IP", "8.8.8.8")
                .header("X-Forwarded-For", "9.9.9.9")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, capture(forwarded)).block();

        ServerWebExchange result = forwarded.get();

        assertNotNull(result);
        assertNull(result.getRequest().getHeaders().getFirst("X-User-Id"));
        assertNull(result.getRequest().getHeaders().getFirst("X-User-Role"));
        assertEquals("test-gateway-token", result.getRequest().getHeaders().getFirst("X-Gateway-Token"));
        assertEquals("127.0.0.1", result.getRequest().getHeaders().getFirst("X-Client-IP"));

        verify(gatewayRateLimiter).acquire("auth", "127.0.0.1", 3, Duration.ofMinutes(1));
    }

    @Test
    void ownResourceUsesClaimsInsteadOfForgedHeaders() {
        when(jwtUtils.parseToken("valid-token"))
                .thenReturn(claims(7L, "alice", 2));

        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.GET, "/users/7")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 50101))
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .header("X-User-Id", "1")
                .header("X-User-Name", "admin")
                .header("X-User-Role", "0")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, capture(forwarded)).block();

        ServerWebExchange result = forwarded.get();

        assertNotNull(result);
        assertEquals("7", result.getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("alice", result.getRequest().getHeaders().getFirst("X-User-Name"));
        assertEquals("2", result.getRequest().getHeaders().getFirst("X-User-Role"));
        assertEquals("test-gateway-token", result.getRequest().getHeaders().getFirst("X-Gateway-Token"));
    }

    @Test
    void alertWebSocketTerminatesJwtAtGatewayAndForwardsTrustedIdentity() {
        when(jwtUtils.parseToken("browser.jwt.value"))
                .thenReturn(claims(1L, "admin", 0));
        when(gatewayRateLimiter.acquire("websocket", "1", 3, Duration.ofMinutes(1)))
                .thenReturn(Mono.just(GatewayRateLimiter.Decision.allowed(60L, false)));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/ws/alerts")
                .header("Sec-WebSocket-Protocol", "access_token, browser.jwt.value")
                .header("X-User-Id", "999")
                .header("X-User-Role", "2")
                .build();

        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(MockServerWebExchange.from(request), capture(forwarded)).block();

        ServerWebExchange result = forwarded.get();

        assertNotNull(result);
        assertEquals("access_token", result.getRequest().getHeaders().getFirst("Sec-WebSocket-Protocol"));
        assertFalse(result.getRequest().getHeaders().getFirst("Sec-WebSocket-Protocol").contains("browser.jwt.value"));
        assertEquals("1", result.getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("admin", result.getRequest().getHeaders().getFirst("X-User-Name"));
        assertEquals("0", result.getRequest().getHeaders().getFirst("X-User-Role"));
        assertEquals("test-gateway-token", result.getRequest().getHeaders().getFirst("X-Gateway-Token"));
    }

    @Test
    void otherUsersResourceIsForbidden() {
        when(jwtUtils.parseToken("valid-token"))
                .thenReturn(claims(7L, "alice", 2));

        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.GET, "/users/8")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, capture(forwarded)).block();

        assertNull(forwarded.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertTrue(exchange.getResponse().getBodyAsString().block().contains("\"code\":403"));
    }

    @Test
    void commerceWriteRateLimitReturnsRetryAfter() {
        when(jwtUtils.parseToken("valid-token")).thenReturn(claims(7L, "alice", 2));

        when(gatewayRateLimiter.acquire("commerce-write", "7", 3, Duration.ofMinutes(1)))
                .thenReturn(Mono.just(GatewayRateLimiter.Decision.rejected(17L, false)));

        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.POST, "/entitlements/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, capture(forwarded)).block();

        assertNull(forwarded.get());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("17", exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertTrue(exchange.getResponse().getBodyAsString().block().contains("\"code\":429"));
    }

    @Test
    void localFallbackEnforcesLimitAndSeparatesSubjects() {
        GatewayRateLimiter limiter = localLimiter();

        GatewayRateLimiter.Decision first = limiter.acquire("auth", "client-a", 2, Duration.ofMinutes(1)).block();
        GatewayRateLimiter.Decision second = limiter.acquire("auth", "client-a", 2, Duration.ofMinutes(1)).block();
        GatewayRateLimiter.Decision rejected = limiter.acquire("auth", "client-a", 2, Duration.ofMinutes(1)).block();
        GatewayRateLimiter.Decision anotherSubject = limiter.acquire("auth", "client-b", 2, Duration.ofMinutes(1)).block();

        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(rejected);
        assertNotNull(anotherSubject);

        assertTrue(first.isAllowed());
        assertTrue(second.isAllowed());
        assertFalse(rejected.isAllowed());
        assertTrue(rejected.isLocalFallback());
        assertTrue(rejected.getRetryAfterSeconds() > 0);
        assertTrue(anotherSubject.isAllowed());
    }

    @Test
    void localFallbackExpiresAndRemainsBounded() throws Exception {
        GatewayRateLimiter limiter = localLimiter();

        assertTrue(limiter.acquire("short", "client-a", 1, Duration.ofSeconds(1)).block().isAllowed());
        assertFalse(limiter.acquire("short", "client-a", 1, Duration.ofSeconds(1)).block().isAllowed());
        Thread.sleep(1200L);
        assertTrue(limiter.acquire("short", "client-a", 1, Duration.ofSeconds(1)).block().isAllowed());

        for (int index = 0; index < 80; index++) {
            limiter.acquire("bounded", "client-" + index, 1, Duration.ofMinutes(1)).block();
        }

        @SuppressWarnings("unchecked")
        Map<String, ?> windows = (Map<String, ?>) ReflectionTestUtils.getField(limiter, "localWindows");

        assertNotNull(windows);
        assertTrue(windows.size() <= 64);
    }

    private GatewayFilterChain capture(
            AtomicReference<ServerWebExchange> forwarded) {

        return exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }

    private Claims claims(Long userId, String username, Integer role) {
        Claims claims = Jwts.claims().setSubject(String.valueOf(userId));
        claims.put("username", username);
        claims.put("role", role);
        return claims;
    }

    @SuppressWarnings("unchecked")
    private GatewayRateLimiter localLimiter() {
        ObjectProvider<ReactiveStringRedisTemplate> provider = mock(ObjectProvider.class);

        when(provider.getIfAvailable()).thenReturn(null);

        GatewayRateLimiter limiter = new GatewayRateLimiter(provider);

        ReflectionTestUtils.setField(limiter, "redisEnabled", true);
        ReflectionTestUtils.setField(limiter, "redisFallbackCooldownMillis", 30000L);
        ReflectionTestUtils.setField(limiter, "fallbackMaxKeys", 64);

        return limiter;
    }
}
