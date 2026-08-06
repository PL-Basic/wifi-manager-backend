package com.plagod.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * 为 Servlet 服务的 Feign 调用注入内部凭证并传递已验证的请求上下文。
 */
public class TrustedFeignRequestInterceptor implements RequestInterceptor {

    private final String internalToken;

    public TrustedFeignRequestInterceptor(String internalToken) {
        if (!StringUtils.hasText(internalToken)
                || internalToken.getBytes(StandardCharsets.UTF_8).length < 16) {
            throw new IllegalStateException("Feign 出站 Internal Token 必须配置且不能少于16字节");
        }
        this.internalToken = internalToken;
    }

    @Override
    public void apply(RequestTemplate template) {
        // Feign 调用不得继承浏览器认证材料，服务间身份只使用 Internal Token。
        template.removeHeader(TrustedHeaderNames.AUTHORIZATION);
        template.removeHeader(TrustedHeaderNames.COOKIE);
        template.removeHeader(TrustedHeaderNames.GATEWAY_TOKEN);
        template.removeHeader(TrustedHeaderNames.INTERNAL_TOKEN);
        template.header(TrustedHeaderNames.INTERNAL_TOKEN, internalToken);

        // 先删除调用方手工提供的上下文，避免绕过可信 Servlet 请求来源。
        for (String headerName : TrustedHeaderNames.PROPAGATED_CONTEXT_HEADERS) {
            template.removeHeader(headerName);
        }

        HttpServletRequest request = currentTrustedRequest();
        if (request == null) {
            return;
        }
        for (String headerName : TrustedHeaderNames.PROPAGATED_CONTEXT_HEADERS) {
            List<String> values = headerValues(request, headerName);
            if (!values.isEmpty()) {
                template.header(headerName, values);
            }
        }
    }

    private List<String> headerValues(HttpServletRequest request, String headerName) {
        List<String> result = new ArrayList<>();
        Enumeration<String> values = request.getHeaders(headerName);
        if (values == null) {
            return result;
        }
        while (values.hasMoreElements()) {
            String value = values.nextElement();
            if (StringUtils.hasText(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private HttpServletRequest currentTrustedRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            return null;
        }

        HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
        Object source = request.getAttribute(TrustedHeaderNames.TRUSTED_SOURCE_ATTRIBUTE);
        if (TrustedHeaderNames.SOURCE_GATEWAY.equals(source)
                || TrustedHeaderNames.SOURCE_INTERNAL.equals(source)) {
            return request;
        }
        return null;
    }
}
