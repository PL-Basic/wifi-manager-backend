package com.plagod.audit;

import com.plagod.security.TrustedContextType;
import com.plagod.security.TrustedRequestContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

final class AuditScopeResolver {

    private static final String SCOPE_PLATFORM = "PLATFORM";
    private static final String SCOPE_TENANT = "TENANT";

    private AuditScopeResolver() {
    }

    static AuditScope resolve(Audited audited,
                              TrustedRequestContext context,
                              Method method,
                              Object[] arguments) {
        if (audited.scope() == Audited.Scope.PLATFORM) {
            return new AuditScope(null, SCOPE_PLATFORM, false);
        }
        if (audited.scope() == Audited.Scope.CONTEXT) {
            return resolveTrustedContext(context);
        }
        return resolveTenant(
                audited.tenantIdSource(),
                context,
                method,
                arguments);
    }

    private static AuditScope resolveTrustedContext(
            TrustedRequestContext context) {
        if (context == null) {
            throw invalidContext();
        }
        TrustedContextType contextType = context.getContextType();
        if (contextType == TrustedContextType.PLATFORM) {
            return new AuditScope(null, SCOPE_PLATFORM, false);
        }
        if (contextType == TrustedContextType.TENANT) {
            return new AuditScope(
                    positiveTenantId(context.getTenantId()),
                    SCOPE_TENANT,
                    false);
        }
        if (contextType == TrustedContextType.PLATFORM_TENANT) {
            return new AuditScope(
                    positiveTenantId(context.getTenantId()),
                    SCOPE_TENANT,
                    true);
        }
        if (context.getTenantId() != null) {
            return new AuditScope(
                    positiveTenantId(context.getTenantId()),
                    SCOPE_TENANT,
                    false);
        }
        return new AuditScope(null, SCOPE_PLATFORM, false);
    }

    private static AuditScope resolveTenant(
                                            Audited.TenantIdSource source,
                                            TrustedRequestContext context,
                                            Method method,
                                            Object[] arguments) {
        if (source == Audited.TenantIdSource.REQUEST) {
            AuditScope contextScope = resolveTrustedContext(context);
            if (!SCOPE_TENANT.equals(contextScope.getScopeType())) {
                throw invalidContext();
            }
            return contextScope;
        }

        Long argumentTenantId = tenantIdArgument(method, arguments);
        if (context != null && context.getTenantId() != null) {
            if (!argumentTenantId.equals(
                    positiveTenantId(context.getTenantId()))) {
                throw invalidContext();
            }
        }
        return new AuditScope(
                argumentTenantId,
                SCOPE_TENANT,
                context != null
                        && context.getContextType()
                        == TrustedContextType.PLATFORM_TENANT);
    }

    private static Long tenantIdArgument(Method method,
                                         Object[] arguments) {
        Annotation[][] parameterAnnotations =
                method.getParameterAnnotations();
        int markedIndex = -1;
        for (int index = 0; index < parameterAnnotations.length; index++) {
            for (Annotation annotation : parameterAnnotations[index]) {
                if (annotation.annotationType() == AuditTenantId.class) {
                    if (markedIndex >= 0) {
                        throw invalidContext();
                    }
                    markedIndex = index;
                }
            }
        }
        if (markedIndex < 0) {
            throw invalidContext();
        }
        if (method.getParameterTypes()[markedIndex] != Long.class
                && method.getParameterTypes()[markedIndex] != long.class) {
            throw invalidContext();
        }
        if (arguments == null || markedIndex >= arguments.length
                || !(arguments[markedIndex] instanceof Number)) {
            throw invalidContext();
        }
        return positiveTenantId(
                String.valueOf(arguments[markedIndex]));
    }

    private static Long positiveTenantId(String value) {
        if (value == null) {
            throw invalidContext();
        }
        try {
            long tenantId = Long.parseLong(value);
            if (tenantId <= 0) {
                throw invalidContext();
            }
            return tenantId;
        } catch (NumberFormatException exception) {
            throw invalidContext();
        }
    }

    private static IllegalArgumentException invalidContext() {
        return new IllegalArgumentException(
                "trusted audit scope is missing or invalid");
    }
}
