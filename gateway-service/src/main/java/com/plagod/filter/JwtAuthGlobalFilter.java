package com.plagod.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.dto.ApiResponse;
import com.plagod.ratelimit.GatewayRateLimiter;
import com.plagod.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String GATEWAY_TOKEN_HEADER = "X-Gateway-Token";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String SEC_WEBSOCKET_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
    private static final String LOCAL_DEMO_CALLBACK = "/payment/callbacks/local-demo";
    private static final String CLIENT_IP_HEADER = "X-Client-IP";

    private static final Set<String> TRUST_HEADERS = new HashSet<>(Arrays.asList(
            "X-User-Id", "X-User-Name", "X-User-Role",
            GATEWAY_TOKEN_HEADER, INTERNAL_TOKEN_HEADER,
            CLIENT_IP_HEADER
    ));

    private static final Set<String> AUTH_WHITE_PATHS = new HashSet<>(Arrays.asList(
            "/auth/login", "/auth/register", "/auth/codes",
            "/auth/code-login", "/auth/reset-password"
    ));

    private static final Pattern USER_SELF = Pattern.compile("^/users/(\\d+)$");
    private static final Pattern USER_AVATAR = Pattern.compile("^/users/(\\d+)/avatar$");
    private static final Pattern USER_PURGE_REQUEST = Pattern.compile("^/users/(\\d+)/purge-requests$");
    private static final Pattern PORTAL_STATUS = Pattern.compile("^/sessions/(\\d+)/portal-status$");
    private static final Pattern SESSION_LOGOUT = Pattern.compile("^/sessions/(\\d+)/logout$");
    private static final Pattern LOCATION_REPORT = Pattern.compile("^/locations/sessions/(\\d+)/report$");

    private static final Pattern ENTITLEMENT_ORDER_DETAIL = Pattern.compile("^/entitlements/orders/[A-Za-z0-9_-]{1,64}$");
    private static final Pattern ENTITLEMENT_ORDER_CANCEL = Pattern.compile("^/entitlements/orders/[A-Za-z0-9_-]{1,64}/cancel$");
    private static final Pattern ENTITLEMENT_PAYMENT_CREATE = Pattern.compile("^/entitlements/orders/[A-Za-z0-9_-]{1,64}/payments$");
    private static final Pattern ENTITLEMENT_PAYMENT_DETAIL = Pattern.compile("^/entitlements/payments/[A-Za-z0-9_-]{1,64}$");
    private static final Pattern ENTITLEMENT_PAYMENT_DEMO_COMPLETE = Pattern.compile("^/entitlements/payments/[A-Za-z0-9_-]{1,64}/demo-complete$");
    private static final Pattern ENTITLEMENT_REFUND_DETAIL = Pattern.compile("^/entitlements/refunds/[A-Za-z0-9_-]{1,64}$");

    private static final Pattern OAUTH_AUTHORIZE = Pattern.compile("^/auth/oauth/(github|qq|wechat)/authorize$");
    private static final Pattern OAUTH_CALLBACK = Pattern.compile("^/auth/oauth/(github|qq|wechat)/callback$");
    private static final Pattern OAUTH_BIND = Pattern.compile("^/auth/oauth/(github|qq|wechat)/bind$");
    private static final Pattern USER_SOCIAL_IDENTITIES = Pattern.compile("^/users/(\\d+)/social-identities$");
    private static final Pattern USER_SOCIAL_IDENTITY_DETAIL = Pattern.compile("^/users/(\\d+)/social-identities/(\\d+)$");


    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GatewayRateLimiter gatewayRateLimiter;

    @Value("${wifi.rate-limit.commerce-write-per-minute:30}")
    private int commerceWriteLimit;

    @Value("${wifi.security.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    @Value("${wifi.security.gateway-token}")
    private String gatewayToken;

    @Value("${wifi.rate-limit.payment-callback-per-minute:120}")
    private int paymentCallbackLimit;

    @Value("${wifi.rate-limit.oauth-per-minute:20}")
    private int oauthLimit;

    @Value("${wifi.rate-limit.auth-per-minute:30}")
    private int authLimit;

    @Value("${wifi.rate-limit.portal-per-minute:12}")
    private int portalLimit;

    @Value("${wifi.rate-limit.location-per-minute:60}")
    private int locationLimit;

    @Value("${wifi.rate-limit.websocket-per-minute:10}")
    private int websocketLimit;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange cleanExchange = removeUntrustedHeaders(exchange);
        ServerHttpRequest request = cleanExchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        if (!StringUtils.hasText(gatewayToken)) {
            return reject(cleanExchange, HttpStatus.INTERNAL_SERVER_ERROR, 500, "Gateway 可信凭据未配置");
        }

        if (isWhitePath(path, method)) {
            return filterWhitePathRateLimit(cleanExchange, chain, path);
        }

        String token = extractToken(request, path);
        if (!StringUtils.hasText(token)) {
            return reject(cleanExchange, HttpStatus.UNAUTHORIZED, 401, "未提供有效登录凭据");
        }

        try {
            Claims claims = jwtUtils.parseToken(token);
            Long userId = JwtUtils.getUserId(claims);
            String username = claims.get("username", String.class);
            Integer role = parseRole(claims.get("role"));

            if (userId == null || userId <= 0 || !StringUtils.hasText(username) || !isSupportedRole(role)) {
                return reject(cleanExchange, HttpStatus.UNAUTHORIZED, 401, "登录凭据内容无效");
            }

            if (!isAllowed(path, method, userId, role)) {
                return reject(cleanExchange, HttpStatus.FORBIDDEN, 403, "无权访问该资源");
            }

            return filterProtectedRateLimit(cleanExchange, chain, path, method, userId, username, role);

        } catch (Exception exception) {
            return reject(cleanExchange, HttpStatus.UNAUTHORIZED, 401, "登录凭据无效或已经过期");
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private ServerWebExchange removeUntrustedHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> TRUST_HEADERS.forEach(headers::remove))
                .build();
        return exchange.mutate().request(request).build();
    }

    private ServerWebExchange addTrustedHeaders(ServerWebExchange exchange, Long userId, String username, Integer role) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    TRUST_HEADERS.forEach(headers::remove);
                    headers.set(GATEWAY_TOKEN_HEADER, gatewayToken);
                    headers.set(CLIENT_IP_HEADER, clientIp(exchange.getRequest()));
                    if (userId != null) {
                        headers.set("X-User-Id", String.valueOf(userId));
                        headers.set("X-User-Name", username);
                        headers.set("X-User-Role", String.valueOf(role));
                    }
                }).build();

        return exchange.mutate().request(request).build();
    }

    private boolean isWhitePath(String path, HttpMethod method) {

        if (AUTH_WHITE_PATHS.contains(path)) {
            return true;
        }

        // 登录授权入口和 Provider 回调允许匿名访问。
        if (HttpMethod.GET.equals(method) && (OAUTH_AUTHORIZE.matcher(path).matches() || OAUTH_CALLBACK.matcher(path).matches())) {
            return true;
        }

        if (LOCAL_DEMO_CALLBACK.equals(path)) {
            return HttpMethod.POST.equals(method);
        }

        return HttpMethod.GET.equals(method) && path.startsWith("/users/avatars/");
    }

    private boolean isAuthRatePath(String path) {
        return "/auth/register".equals(path)
                || "/auth/login".equals(path)
                || "/auth/code-login".equals(path)
                || "/auth/codes".equals(path)
                || "/auth/reset-password".equals(path);
    }
    private boolean isOAuthRatePath(String path) {
        return OAUTH_AUTHORIZE.matcher(path).matches() || OAUTH_CALLBACK.matcher(path).matches();
    }

    private boolean isAllowed(String path, HttpMethod method, Long userId, Integer role) {
        if ("/ws/alerts".equals(path)) {
            return isAdmin(role);
        }

        if ("/admin".equals(path) || path.startsWith("/admin/")) {
            return isAdmin(role);
        }

        // 绑定入口必须先通过 JWT，随后由 Gateway 注入可信用户身份。
        if (HttpMethod.GET.equals(method) && OAUTH_BIND.matcher(path).matches()) {
            return true;
        }

        Matcher matcher = USER_SOCIAL_IDENTITIES.matcher(path);
        if (matcher.matches()) {
            return HttpMethod.GET.equals(method) && ownsPathUser(userId, matcher);
        }

        matcher = USER_SOCIAL_IDENTITY_DETAIL.matcher(path);
        if (matcher.matches()) {
            return HttpMethod.DELETE.equals(method) && ownsPathUser(userId, matcher);
        }

        matcher = USER_SELF.matcher(path);
        if (matcher.matches()) {
            return (HttpMethod.GET.equals(method) || HttpMethod.PUT.equals(method)) && ownsPathUser(userId, matcher);
        }

        matcher = USER_AVATAR.matcher(path);
        if (matcher.matches()) {
            return HttpMethod.POST.equals(method) && ownsPathUser(userId, matcher);
        }

        matcher = USER_PURGE_REQUEST.matcher(path);
        if (matcher.matches()) {
            return HttpMethod.POST.equals(method) && ownsPathUser(userId, matcher);
        }

        if ("/entitlements/products".equals(path)) {
            return HttpMethod.GET.equals(method);
        }

        if ("/entitlements/orders".equals(path)) {
            return HttpMethod.GET.equals(method) || HttpMethod.POST.equals(method);
        }

        if ("/entitlements/refunds".equals(path)) {
            return HttpMethod.GET.equals(method) || HttpMethod.POST.equals(method);
        }

        if ("/entitlements/me".equals(path)
                || "/entitlements/purchases".equals(path)
                || "/entitlements/usage-logs".equals(path)) {
            return HttpMethod.GET.equals(method);
        }

        if (ENTITLEMENT_ORDER_DETAIL.matcher(path).matches()) {
            return HttpMethod.GET.equals(method);
        }

        if (ENTITLEMENT_ORDER_CANCEL.matcher(path).matches()) {
            return HttpMethod.POST.equals(method);
        }

        if (ENTITLEMENT_REFUND_DETAIL.matcher(path).matches()) {
            return HttpMethod.GET.equals(method);
        }

        if (ENTITLEMENT_PAYMENT_CREATE.matcher(path).matches()) {
            return HttpMethod.POST.equals(method);
        }

        if (ENTITLEMENT_PAYMENT_DETAIL.matcher(path).matches()) {
            return HttpMethod.GET.equals(method);
        }

        if (ENTITLEMENT_PAYMENT_DEMO_COMPLETE.matcher(path).matches()) {
            return HttpMethod.POST.equals(method);
        }

        if ("/sessions/portal-authorize".equals(path)) {
            return HttpMethod.POST.equals(method);
        }

        if ("/sessions".equals(path)) {
            return HttpMethod.GET.equals(method);
        }

        if (PORTAL_STATUS.matcher(path).matches()) {
            return HttpMethod.GET.equals(method);
        }

        if (SESSION_LOGOUT.matcher(path).matches()) {
            return HttpMethod.POST.equals(method);
        }

        if ("/traffic".equals(path) || "/client-signals".equals(path)) {
            return HttpMethod.GET.equals(method);
        }

        if ("/locations".equals(path)) {
            return HttpMethod.GET.equals(method);
        }

        if ("/locations/consent".equals(path)) {
            return HttpMethod.GET.equals(method) || HttpMethod.POST.equals(method) || HttpMethod.DELETE.equals(method);
        }

        if ("/locations/history".equals(path)) {
            return HttpMethod.DELETE.equals(method);
        }

        if (LOCATION_REPORT.matcher(path).matches()) {
            return HttpMethod.POST.equals(method);
        }

        // 不再使用“没有明确禁止就放行”的策略。
        return false;
    }

    private boolean ownsPathUser(Long userId, Matcher matcher) {
        return String.valueOf(userId).equals(matcher.group(1));
    }

    private boolean isAdmin(Integer role) {
        return Integer.valueOf(0).equals(role) || Integer.valueOf(1).equals(role);
    }

    private boolean isSupportedRole(Integer role) {
        return Integer.valueOf(0).equals(role) || Integer.valueOf(1).equals(role) || Integer.valueOf(2).equals(role);
    }

    private Integer parseRole(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("JWT 缺少角色");
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private String extractToken(ServerHttpRequest request, String path) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }

        if ("/ws/alerts".equals(path)) {
            return extractWebSocketProtocolToken(request);
        }

        return null;
    }

    private String extractWebSocketProtocolToken(ServerHttpRequest request) {
        List<String> protocols = new ArrayList<>();

        for (String header : request.getHeaders().getOrEmpty(SEC_WEBSOCKET_PROTOCOL_HEADER)) {
            for (String item : header.split(",")) {
                if (StringUtils.hasText(item)) {
                    protocols.add(item.trim());
                }
            }
        }

        for (int i = 0; i + 1 < protocols.size(); i++) {
            if ("access_token".equals(protocols.get(i))) {
                return protocols.get(i + 1);
            }
        }

        return null;
    }

    private String clientIp(ServerHttpRequest request) {
        if (trustProxyHeaders) {
            String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");

            if (StringUtils.hasText(forwardedFor)) {
                String firstAddress = forwardedFor.split(",")[0].trim();

                if (StringUtils.hasText(firstAddress)) {
                    return firstAddress;
                }
            }

            String realIp = request.getHeaders().getFirst("X-Real-IP");

            if (StringUtils.hasText(realIp)) {
                return realIp.trim();
            }
        }

        InetSocketAddress address = request.getRemoteAddress();

        if (address == null || address.getAddress() == null) {
            return "unknown";
        }

        return address.getAddress().getHostAddress();
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, int code, String message) {

        return reject(exchange, status, code, message, null);
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, int code, String message, Long retryAfterSeconds) {

        byte[] body;

        try {
            body = objectMapper.writeValueAsBytes(ApiResponse.fail(code, message));
        } catch (Exception exception) {
            body = ("{\"code\":" + code + ",\"message\":\"请求处理失败\",\"data\":null}").getBytes(StandardCharsets.UTF_8);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setContentLength(body.length);

        if (retryAfterSeconds != null) {
            exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1L, retryAfterSeconds)));
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> filterWhitePathRateLimit(ServerWebExchange exchange, GatewayFilterChain chain, String path) {

        String ip = clientIp(exchange.getRequest());

        if (isOAuthRatePath(path)) {
            return rateLimitOrContinue(exchange, chain, "oauth", ip, oauthLimit, "OAuth 请求过于频繁，请稍后再试", null, null, null);
        }

        if (isAuthRatePath(path)) {
            return rateLimitOrContinue(exchange, chain, "auth", ip, authLimit, "认证请求过于频繁，请稍后再试", null, null, null);
        }

        if (LOCAL_DEMO_CALLBACK.equals(path)) {
            return rateLimitOrContinue(exchange, chain, "payment-callback", ip, paymentCallbackLimit, "支付回调请求过于频繁", null, null, null);
        }

        return chain.filter(addTrustedHeaders(exchange, null, null, null));
    }

    private Mono<Void> filterProtectedRateLimit(ServerWebExchange exchange, GatewayFilterChain chain, String path, HttpMethod method, Long userId, String username, Integer role) {

        String scene = null;
        int limit = 0;

        if (OAUTH_BIND.matcher(path).matches()) {
            scene = "oauth-bind";
            limit = oauthLimit;
        } else if ("/sessions/portal-authorize".equals(path)) {
            scene = "portal";
            limit = portalLimit;
        } else if (LOCATION_REPORT.matcher(path).matches()) {
            scene = "location";
            limit = locationLimit;
        } else if ("/ws/alerts".equals(path)) {
            scene = "websocket";
            limit = websocketLimit;
        } else if (isCommerceWritePath(path, method)) {
            scene = "commerce-write";
            limit = commerceWriteLimit;
        }

        if (scene == null) {
            return chain.filter(addTrustedHeaders(exchange, userId, username, role));
        }

        return rateLimitOrContinue(exchange, chain, scene, String.valueOf(userId), limit, "请求过于频繁，请稍后再试", userId, username, role);
    }

    private Mono<Void> rateLimitOrContinue(ServerWebExchange exchange, GatewayFilterChain chain, String scene, String subject, int limit, String message, Long userId, String username, Integer role) {

        return gatewayRateLimiter
                .acquire(scene, subject, limit, Duration.ofMinutes(1))
                .flatMap(decision -> {
                    if (!decision.isAllowed()) {
                        return reject(exchange, HttpStatus.TOO_MANY_REQUESTS, 429, message, decision.getRetryAfterSeconds());
                    }

                    return chain.filter(addTrustedHeaders(exchange, userId, username, role));
                });
    }

    private boolean isCommerceWritePath(String path, HttpMethod method) {
        if (!HttpMethod.POST.equals(method)) {
            return false;
        }

        return "/entitlements/orders".equals(path)
                || "/entitlements/refunds".equals(path)
                || ENTITLEMENT_ORDER_CANCEL.matcher(path).matches()
                || ENTITLEMENT_PAYMENT_CREATE.matcher(path).matches()
                || ENTITLEMENT_PAYMENT_DEMO_COMPLETE.matcher(path).matches();
    }

}