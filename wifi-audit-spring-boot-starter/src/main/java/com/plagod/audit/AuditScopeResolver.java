package com.plagod.audit;

import com.plagod.security.TrustedHeaderNames;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Locale;

final class AuditScopeResolver {

    private static final String CONTEXT_PLATFORM = "PLATFORM";
    private static final String CONTEXT_PLATFORM_TENANT = "PLATFORM_TENANT";
    private static final String CONTEXT_TENANT = "TENANT";
    private static final String SCOPE_PLATFORM = "PLATFORM";
    private static final String SCOPE_TENANT = "TENANT";

    private AuditScopeResolver() {
    }

    static AuditScope resolve(Audited audited,
                              Method method,
                              Object[] arguments,
                              HttpServletRequest request) {
        if (audited.scope() == Audited.Scope.PLATFORM) {
            requireRequestSource(audited);
            requireTrustedRequest(request);
            return new AuditScope(null, SCOPE_PLATFORM);
        }
        if (audited.scope() == Audited.Scope.CONTEXT) {
            requireRequestSource(audited);
            return resolveTrustedContext(request);
        }
        return resolveTenant(
                audited.tenantIdSource(),
                method,
                arguments,
                request);
    }

    private static AuditScope resolveTrustedContext(
            HttpServletRequest request) {
        requireTrustedRequest(request);
        String contextType = normalizedHeader(
                request,
                TrustedHeaderNames.CONTEXT_TYPE);
        String tenantIdValue = normalizedHeader(
                request,
                TrustedHeaderNames.TENANT_ID);

        if (CONTEXT_PLATFORM.equals(contextType)) {
            if (tenantIdValue != null) {
                throw invalidContext();
            }
            return new AuditScope(null, SCOPE_PLATFORM);
        }

        if (CONTEXT_TENANT.equals(contextType)
                || CONTEXT_PLATFORM_TENANT.equals(contextType)) {
            return new AuditScope(positiveTenantId(tenantIdValue), SCOPE_TENANT);
        }

        throw invalidContext();
    }

    private static AuditScope resolveTenant(
                                            Audited.TenantIdSource source,
                                            Method method,
                                            Object[] arguments,
                                            HttpServletRequest request) {
        if (source == Audited.TenantIdSource.REQUEST) {
            AuditScope contextScope = resolveTrustedContext(request);
            if (!SCOPE_TENANT.equals(contextScope.getScopeType())) {
                throw invalidContext();
            }
            return contextScope;
        }

        Long argumentTenantId = tenantIdArgument(method, arguments);
        if (request != null) {
            requireTrustedRequest(request);
            String contextType = normalizedHeader(
                    request,
                    TrustedHeaderNames.CONTEXT_TYPE);
            String headerTenantId = normalizedHeader(
                    request,
                    TrustedHeaderNames.TENANT_ID);
            if (CONTEXT_TENANT.equals(contextType)
                    || CONTEXT_PLATFORM_TENANT.equals(contextType)) {
                if (!argumentTenantId.equals(
                        positiveTenantId(headerTenantId))) {
                    throw invalidContext();
                }
            } else {
                throw invalidContext();
            }
        }
        return new AuditScope(argumentTenantId, SCOPE_TENANT);
    }

    private static void requireRequestSource(Audited audited) {
        if (audited.tenantIdSource()
                != Audited.TenantIdSource.REQUEST) {
            throw invalidContext();
        }
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

    private static void requireTrustedRequest(HttpServletRequest request) {
        if (request == null) {
            throw invalidContext();
        }

        Object source = request.getAttribute(
                TrustedHeaderNames.TRUSTED_SOURCE_ATTRIBUTE);
        if (!TrustedHeaderNames.SOURCE_GATEWAY.equals(source)
                && !TrustedHeaderNames.SOURCE_INTERNAL.equals(source)) {
            throw invalidContext();
        }
    }

    private static String normalizedHeader(
            HttpServletRequest request,
            String name) {
        String value = request.getHeader(name);
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : null;
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
