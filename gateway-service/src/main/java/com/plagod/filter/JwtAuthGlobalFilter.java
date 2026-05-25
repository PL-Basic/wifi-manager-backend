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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final List<String> WHITE_PATHS = Arrays.asList(
            "/auth/login",
            "/auth/register"
    );
    private static final Pattern USER_SELF_PATH = Pattern.compile("^/users/(\\d+)$");
    private static final int ADMIN_ROLE = 1;

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
            Long userId = JwtUtils.getUserId(claims);
            Integer role = Integer.valueOf(String.valueOf(claims.get("role")));
            if (!isAllowed(path, userId, role)) {
                return forbidden(exchange);
            }
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-User-Name", String.valueOf(claims.get("username")))
                    .header("X-User-Role", String.valueOf(role))
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

    private boolean isAllowed(String path, Long userId, Integer role) {
        if (Integer.valueOf(ADMIN_ROLE).equals(role)) {
            return true;
        }
        if (path.startsWith("/admin/")) {
            return false;
        }
        if ("/users".equals(path) || "/users/stats".equals(path)) {
            return false;
        }
        if (path.startsWith("/users/")) {
            Matcher matcher = USER_SELF_PATH.matcher(path);
            return matcher.matches() && String.valueOf(userId).equals(matcher.group(1));
        }
        return true;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }
}
