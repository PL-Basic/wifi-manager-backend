package com.plagod.security;

import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.logging.Logger;

public class TrustedRequestFilter extends OncePerRequestFilter {

    private static final Logger log = Logger.getLogger(TrustedRequestFilter.class.getName());

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
        String gatewayToken = request.getHeader(TrustedHeaderNames.GATEWAY_TOKEN);
        String internalToken = request.getHeader(TrustedHeaderNames.INTERNAL_TOKEN);

        boolean trusted;
        String trustedSource;

        if (properties.isInternalPath(path)) {
            // 内部路径只能使用内部凭据，Gateway 凭据不能越权调用。
            trusted = tokenMatches(internalToken, properties.getInternalToken());
            trustedSource = TrustedHeaderNames.SOURCE_INTERNAL;
        } else {
            // 迁移期间允许 Gateway 或可信 Feign 调用现有业务路径。
            boolean gatewayTrusted = tokenMatches(gatewayToken, properties.getGatewayToken());
            boolean internalTrusted = tokenMatches(internalToken, properties.getInternalToken());
            trusted = gatewayTrusted || internalTrusted;
            trustedSource = gatewayTrusted
                    ? TrustedHeaderNames.SOURCE_GATEWAY
                    : TrustedHeaderNames.SOURCE_INTERNAL;
        }

        if (!trusted) {
            String reason = properties.isInternalPath(path)
                    ? "INTERNAL_TOKEN_MISMATCH"
                    : "TRUSTED_TOKEN_MISMATCH";

            log.warning(String.format(
                    "trusted request rejected: reason=%s, method=%s, path=%s, internalPath=%s, gatewayTokenPresent=%s, internalTokenPresent=%s",
                    reason,
                    request.getMethod(),
                    path,
                    properties.isInternalPath(path),
                    StringUtils.hasText(gatewayToken),
                    StringUtils.hasText(internalToken)));

            reject(response);
            return;
        }

        request.setAttribute(TrustedHeaderNames.TRUSTED_SOURCE_ATTRIBUTE, trustedSource);
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
