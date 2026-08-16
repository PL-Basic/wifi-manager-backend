package com.plagod.security;

import com.plagod.exception.ApiStatusException;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * 将共享 Resolver 的认证结果适配为 Monitor Web 层统一异常契约。
 */
@Component
public class MonitorTrustedRequestContextProvider {

    private final TrustedRequestContextResolver contextResolver;

    public MonitorTrustedRequestContextProvider(
            TrustedRequestContextResolver contextResolver) {
        this.contextResolver = contextResolver;
    }

    public TrustedRequestContext resolve(HttpServletRequest request) {
        try {
            return contextResolver.resolve(request);
        } catch (TrustedRequestContextException exception) {
            throw new ApiStatusException(
                    exception.getHttpStatus(),
                    exception.getHttpStatus(),
                    exception.getErrorKey(),
                    exception.getMessage());
        }
    }
}
