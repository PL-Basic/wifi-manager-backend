package com.plagod.security;

import com.plagod.exception.ApiStatusException;
import org.springframework.stereotype.Component;

/**
 * Monitor 租户资源的本地作用域门禁。
 */
@Component
public class MonitorTenantScope {

    public Long requireTenantId(TrustedRequestContext context) {
        if (context == null) {
            throw ApiStatusException.authenticationRequired(
                    "可信请求上下文缺失");
        }
        if (!context.hasUserActor()) {
            throw ApiStatusException.forbidden(
                    "内部服务身份不能直接访问租户资源");
        }
        if (context.getContextType() == TrustedContextType.PLATFORM) {
            throw ApiStatusException.forbidden(
                    "平台上下文必须先进入目标租户");
        }
        if (context.getContextType() != TrustedContextType.TENANT
                && context.getContextType()
                != TrustedContextType.PLATFORM_TENANT) {
            throw ApiStatusException.forbidden(
                    "当前上下文不能访问租户资源");
        }
        return Long.valueOf(context.getTenantId());
    }
}
