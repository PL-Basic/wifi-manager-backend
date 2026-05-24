package com.plagod.filter;

import com.plagod.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final List<String> WHITE_PATHS = Arrays.asList(
            "/auth/login",
            "/auth/register"
    );

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isWhitePath(path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange);
        }

        String token = authorization.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtUtils.parseToken(token);
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .header("X-User-Id", String.valueOf(JwtUtils.getUserId(claims)))
                    .header("X-User-Name", String.valueOf(claims.get("username")))
                    .header("X-User-Role", String.valueOf(claims.get("role")))
                    .build();
            return chain.filter(exchange.mutate().request(request).build());
        } catch (Exception ex) {
            return unauthorized(exchange);
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isWhitePath(String path) {
        return WHITE_PATHS.contains(path);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
