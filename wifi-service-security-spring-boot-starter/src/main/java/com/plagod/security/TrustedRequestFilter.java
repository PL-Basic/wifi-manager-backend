package com.plagod.security;

import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class TrustedRequestFilter extends OncePerRequestFilter {

    private static final String GATEWAY_HEADER = "X-Gateway-Token";
    private static final String INTERNAL_HEADER = "X-Internal-Token";

    private final TrustedRequestProperties properties;

    public TrustedRequestFilter(TrustedRequestProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws java.io.IOException,
            javax.servlet.ServletException {

        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String gatewayToken = request.getHeader(GATEWAY_HEADER);
        String internalToken = request.getHeader(INTERNAL_HEADER);

        boolean trusted;

        if (properties.isInternalPath(path)) {
            // 内部路径只能使用内部凭据，Gateway 凭据不能越权调用。
            trusted = tokenMatches(internalToken, properties.getInternalToken());
        } else {
            // 迁移期间允许 Gateway 或可信 Feign 调用现有业务路径。
            trusted = tokenMatches(gatewayToken, properties.getGatewayToken()) || tokenMatches(internalToken, properties.getInternalToken());
        }

        if (!trusted) {
            reject(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean tokenMatches(String supplied, String expected) {
        if (!StringUtils.hasText(supplied) || !StringUtils.hasText(expected)) {
            return false;
        }

        return MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletResponse response) throws java.io.IOException {
        byte[] body = ("{\"code\":401,\"message\":\"服务请求来源认证失败\",\"data\":null}").getBytes(StandardCharsets.UTF_8);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json; charset=utf-8");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}