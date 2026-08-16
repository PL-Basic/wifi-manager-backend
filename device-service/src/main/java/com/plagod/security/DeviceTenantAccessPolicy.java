package com.plagod.security;

import com.plagod.exception.ApiStatusException;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * 设备域租户管理接口的可信上下文边界。
 */
@Component
public class DeviceTenantAccessPolicy {

    private static final String TENANT_OWNER = "TENANT_OWNER";
    private static final String TENANT_ADMIN = "TENANT_ADMIN";

    private final TrustedRequestContextResolver contextResolver;

    public DeviceTenantAccessPolicy(
            TrustedRequestContextResolver contextResolver) {
        this.contextResolver = contextResolver;
    }

    public Long requireAdminTenantId(HttpServletRequest request) {
        TrustedRequestContext context = resolveContext(request);
        if (context == null
                || !context.hasUserActor()
                || context.getTrustedSource()
                != TrustedSource.INTERNAL_SERVICE) {
            throw ApiStatusException.forbidden(
                    "设备租户资源需要已传播的用户上下文");
        }

        if (context.getContextType() == TrustedContextType.TENANT) {
            String tenantRole = context.getTenantRole();
            if (!TENANT_OWNER.equals(tenantRole)
                    && !TENANT_ADMIN.equals(tenantRole)) {
                throw ApiStatusException.forbidden(
                        "当前租户成员无权管理设备");
            }
            return tenantId(context);
        }

        if (context.getContextType()
                == TrustedContextType.PLATFORM_TENANT) {
            return tenantId(context);
        }

        throw ApiStatusException.forbidden(
                "平台上下文不能直接访问租户设备");
    }

    private TrustedRequestContext resolveContext(
            HttpServletRequest request) {
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

    private Long tenantId(TrustedRequestContext context) {
        return Long.valueOf(context.getTenantId());
    }
}
