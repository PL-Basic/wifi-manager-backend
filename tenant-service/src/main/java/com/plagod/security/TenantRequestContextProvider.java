package com.plagod.security;

import com.plagod.exception.ApiStatusException;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * 将共享 Resolver 装配的上下文转换为 tenant-service 的必需用户上下文。
 */
@Component
public class TenantRequestContextProvider {

    private final TrustedRequestContextResolver contextResolver;

    public TenantRequestContextProvider(
            TrustedRequestContextResolver contextResolver) {
        this.contextResolver = contextResolver;
    }

    public TrustedRequestContext requireUserContext(
            HttpServletRequest request) {
        TrustedRequestContext context;
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
                    "请求缺少可信用户上下文");
        }
        if (!context.hasUserActor()) {
            throw ApiStatusException.forbidden(
                    "内部服务身份不能直接访问用户或租户资源");
        }
        return context;
    }
}
