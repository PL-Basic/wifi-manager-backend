package com.plagod.security;

import com.plagod.exception.ApiStatusException;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * user-service 对冻结可信上下文的本域授权规则。
 */
@Component
public class UserRequestContextPolicy {

    private final TrustedRequestContextResolver contextResolver;

    public UserRequestContextPolicy(
            TrustedRequestContextResolver contextResolver) {
        this.contextResolver = contextResolver;
    }

    public TrustedRequestContext requireUserActor(
            HttpServletRequest request) {
        TrustedRequestContext context = resolve(request);
        if (!context.hasUserActor()) {
            throw ApiStatusException.forbidden(
                    "内部服务身份不能代替用户访问资源");
        }
        return context;
    }

    public TrustedRequestContext requireTenantBoundActor(
            HttpServletRequest request) {
        TrustedRequestContext context = requireUserActor(request);
        if (context.getContextType() == TrustedContextType.PLATFORM) {
            throw ApiStatusException.forbidden(
                    "平台上下文不能直接访问租户资源");
        }
        if (context.getContextType() != TrustedContextType.TENANT
                && context.getContextType()
                != TrustedContextType.PLATFORM_TENANT) {
            throw ApiStatusException.forbidden("当前上下文不能访问租户资源");
        }
        return context;
    }

    public TrustedRequestContext requirePlatformActor(
            HttpServletRequest request) {
        TrustedRequestContext context = requireUserActor(request);
        if (context.getContextType() != TrustedContextType.PLATFORM
                || !Integer.valueOf(0).equals(context.getGlobalRole())) {
            throw ApiStatusException.forbidden(
                    "当前上下文不能访问平台资源");
        }
        return context;
    }

    public TrustedRequestContext requireEntitlementAdminActor(
            HttpServletRequest request) {
        TrustedRequestContext context = requireUserActor(request);
        if (context.getContextType()
                == TrustedContextType.PLATFORM_TENANT
                && Integer.valueOf(0).equals(context.getGlobalRole())) {
            return context;
        }
        if (context.getContextType() == TrustedContextType.TENANT
                && ("TENANT_OWNER".equals(context.getTenantRole())
                || "TENANT_ADMIN".equals(context.getTenantRole()))) {
            return context;
        }
        throw ApiStatusException.forbidden(
                "当前上下文没有租户权益管理权限");
    }

    public TrustedRequestContext requirePlatformTenantSuperAdmin(
            HttpServletRequest request) {
        TrustedRequestContext context = requireUserActor(request);
        if (context.getContextType()
                != TrustedContextType.PLATFORM_TENANT
                || !Integer.valueOf(0).equals(context.getGlobalRole())) {
            throw ApiStatusException.forbidden(
                    "仅平台超级管理员可在目标租户执行此操作");
        }
        return context;
    }

    public Long tenantId(TrustedRequestContext context) {
        try {
            return Long.valueOf(context.getTenantId());
        } catch (NumberFormatException exception) {
            throw ApiStatusException.forbidden("可信租户上下文无效");
        }
    }

    public void requireSelf(
            TrustedRequestContext context,
            Long targetUserId) {
        if (targetUserId == null
                || !targetUserId.equals(context.getUserId())) {
            throw ApiStatusException.notFound("用户资源不存在");
        }
    }

    private TrustedRequestContext resolve(HttpServletRequest request) {
        try {
            TrustedRequestContext context =
                    contextResolver.resolve(request);
            if (context == null) {
                throw ApiStatusException.authenticationRequired(
                        "请求缺少可信用户上下文");
            }
            return context;
        } catch (TrustedRequestContextException exception) {
            throw new ApiStatusException(
                    exception.getHttpStatus(),
                    exception.getHttpStatus(),
                    exception.getErrorKey(),
                    exception.getMessage());
        }
    }
}
