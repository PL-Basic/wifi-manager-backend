package com.plagod.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalApiInterceptor implements HandlerInterceptor {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Value("${wifi.internal.token}")
    private String expectedToken;

    @Autowired
    private ObjectMapper objectMapper;


    // token头拦截器
    // 所有 /internal/** 请求进入 Controller 前都会执行该方法。Token 不正确时直接返回 401，Controller 不会被调用。
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String aclToken = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (tokenMatches(aclToken)) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json; charset=utf-8");

        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(401, "内部服务认证失败"));
        return false;
    }

    // token比较
    // 使用固定时间比较，避免普通字符串比较提前结束带来的时序信息泄漏。
    private boolean tokenMatches(String aclToken) {
        if (!StringUtils.hasText(aclToken) || !StringUtils.hasText(expectedToken)) {
            return false;
        }
        return MessageDigest.isEqual(aclToken.getBytes(StandardCharsets.UTF_8), expectedToken.getBytes(StandardCharsets.UTF_8));
    }
}