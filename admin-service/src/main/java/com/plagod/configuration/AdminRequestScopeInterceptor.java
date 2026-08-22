package com.plagod.configuration;

import com.plagod.exception.ApiStatusException;
import com.plagod.security.TrustedContextType;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.TrustedRequestContextException;
import com.plagod.security.TrustedRequestContextResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 在 BFF 调用下游前固定平台与租户资源作用域。
 */
public class AdminRequestScopeInterceptor implements HandlerInterceptor {

    private final TrustedRequestContextResolver contextResolver;

    public AdminRequestScopeInterceptor(
            TrustedRequestContextResolver contextResolver) {
        if (contextResolver == null) {
            throw new IllegalArgumentException(
                    "可信请求上下文 Resolver 不能为空");
        }
        this.contextResolver = contextResolver;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        TrustedRequestContext context = requiredContext(request);
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        AdminContextScopeRequired scope =
                AnnotatedElementUtils.findMergedAnnotation(
                        handlerMethod.getMethod(),
                        AdminContextScopeRequired.class);
        if (scope == null) {
            scope = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(),
                    AdminContextScopeRequired.class);
        }

        if (scope == null) {
            requireTenantResourceContext(context);
        } else if (scope.value()
                == AdminContextScopeRequired.Scope.PLATFORM) {
            requireExactContext(
                    context,
                    TrustedContextType.PLATFORM,
                    "当前接口只能由平台上下文访问");
        } else {
            requireExactContext(
                    context,
                    TrustedContextType.PLATFORM_TENANT,
                    "当前接口只能由平台代管上下文访问");
        }
        return true;
    }

    private TrustedRequestContext requiredContext(
            HttpServletRequest request) {
        final TrustedRequestContext context;
        try {
            context = contextResolver.resolve(request);
        } catch (TrustedRequestContextException exception) {
            throw new ApiStatusException(
                    exception.getHttpStatus(),
                    exception.getHttpStatus(),
                    exception.getErrorKey(),
                    exception.getMessage());
        }
        if (context == null) {
            throw ApiStatusException.authenticationRequired(
                    "BFF 请求缺少可信用户上下文");
        }
        if (!context.hasUserActor()) {
            throw ApiStatusException.forbidden(
                    "内部服务身份不能直接访问用户 BFF 资源");
        }
        return context;
    }

    private void requireExactContext(
            TrustedRequestContext context,
            TrustedContextType expected,
            String message) {
        if (context.getContextType() != expected) {
            throw ApiStatusException.forbidden(message);
        }
    }

    private void requireTenantResourceContext(
            TrustedRequestContext context) {
        TrustedContextType contextType = context.getContextType();
        if (contextType != TrustedContextType.TENANT
                && contextType != TrustedContextType.PLATFORM_TENANT) {
            throw ApiStatusException.forbidden(
                    "访问租户资源前必须进入租户上下文");
        }
    }
}
