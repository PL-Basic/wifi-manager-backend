package com.plagod.security;

import com.plagod.request.RequestId;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;

/**
 * 为 Servlet 服务的 Feign 调用注入内部凭证并传递已验证的请求上下文。
 */
public class TrustedFeignRequestInterceptor implements RequestInterceptor {

    private final String internalToken;
    private final TrustedRequestContextResolver contextResolver;

    public TrustedFeignRequestInterceptor(String internalToken) {
        this(internalToken, new TrustedRequestContextResolver());
    }

    public TrustedFeignRequestInterceptor(
            String internalToken,
            TrustedRequestContextResolver contextResolver) {
        if (!StringUtils.hasText(internalToken)
                || internalToken.getBytes(StandardCharsets.UTF_8).length < 16) {
            throw new IllegalStateException("Feign 出站 Internal Token 必须配置且不能少于16字节");
        }
        if (contextResolver == null) {
            throw new IllegalArgumentException("可信请求上下文 Resolver 不能为空");
        }
        this.internalToken = internalToken;
        this.contextResolver = contextResolver;
    }

    @Override
    public void apply(RequestTemplate template) {
        // Feign 调用不得继承浏览器认证材料，服务间身份只使用 Internal Token。
        template.removeHeader(TrustedRequestHeaders.AUTHORIZATION);
        template.removeHeader(TrustedRequestHeaders.COOKIE);
        template.removeHeader(TrustedRequestHeaders.GATEWAY_TOKEN);
        template.removeHeader(TrustedRequestHeaders.INTERNAL_TOKEN);
        template.header(TrustedRequestHeaders.INTERNAL_TOKEN, internalToken);

        // 先删除调用方手工提供的上下文，避免绕过可信 Servlet 请求来源。
        for (String headerName :
                TrustedRequestHeaders.PROPAGATED_CONTEXT_HEADERS) {
            template.removeHeader(headerName);
        }

        HttpServletRequest request = currentTrustedRequest();
        if (request == null) {
            return;
        }
        TrustedRequestContext context = contextResolver.resolve(request);
        if (context == null) {
            return;
        }
        template.header(RequestId.HEADER_NAME, context.getRequestId());
        if (!context.hasUserActor()) {
            return;
        }
        template.header(
                TrustedRequestHeaders.USER_ID,
                String.valueOf(context.getUserId()));
        template.header(
                TrustedRequestHeaders.USER_ROLE,
                String.valueOf(context.getGlobalRole()));
        template.header(
                TrustedRequestHeaders.SESSION_ID,
                context.getSessionId());
        template.header(
                TrustedRequestHeaders.TOKEN_ID,
                context.getTokenId());
        template.header(
                TrustedRequestHeaders.CONTEXT_TYPE,
                context.getContextType().name());
        setIfPresent(
                template,
                TrustedRequestHeaders.USER_NAME,
                request.getHeader(TrustedRequestHeaders.USER_NAME));
        setIfPresent(
                template,
                TrustedRequestHeaders.TENANT_ID,
                context.getTenantId());
        setIfPresent(
                template,
                TrustedRequestHeaders.TENANT_CODE,
                context.getTenantCode());
        setIfPresent(
                template,
                TrustedRequestHeaders.TENANT_ROLE,
                context.getTenantRole());
        if (context.getTenantContextVersion() != null) {
            template.header(
                    TrustedRequestHeaders.TENANT_CONTEXT_VERSION,
                    String.valueOf(context.getTenantContextVersion()));
        }
        if (context.getMemberContextVersion() != null) {
            template.header(
                    TrustedRequestHeaders.MEMBER_CONTEXT_VERSION,
                    String.valueOf(context.getMemberContextVersion()));
        }
        if (!context.getPlatformAuthorities().isEmpty()) {
            template.header(
                    TrustedRequestHeaders.PLATFORM_AUTHORITIES,
                    String.join(",", context.getPlatformAuthorities()));
        }
    }

    private void setIfPresent(
            RequestTemplate template,
            String name,
            String value) {
        if (StringUtils.hasText(value)) {
            template.header(name, value.trim());
        }
    }

    private HttpServletRequest currentTrustedRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            return null;
        }

        HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
        Object source = request.getAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE);
        if (TrustedRequestHeaders.SOURCE_GATEWAY.equals(source)
                || TrustedRequestHeaders.SOURCE_INTERNAL.equals(source)) {
            return request;
        }
        return null;
    }
}
