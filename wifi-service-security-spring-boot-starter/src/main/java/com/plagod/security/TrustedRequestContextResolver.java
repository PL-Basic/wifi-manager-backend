package com.plagod.security;

import com.plagod.request.RequestId;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 只从 Starter 已认证并标记的请求装配不可变可信上下文。
 */
public class TrustedRequestContextResolver {

    public TrustedRequestContext resolve(HttpServletRequest request) {
        TrustedSource source = trustedSource(request);
        TrustedRequestContext existing = resolved(request);
        if (existing != null) {
            return existing;
        }
        String requestId = requestId(request);
        boolean identityPresent = hasIdentityHeaders(request);

        if (!identityPresent) {
            if (source == TrustedSource.INTERNAL_SERVICE) {
                return attach(
                        request,
                        TrustedRequestContext.internalService(requestId));
            }
            return null;
        }

        Long userId = positiveLong(
                requiredScalar(request, TrustedRequestHeaders.USER_ID),
                "用户ID");
        Integer globalRole = role(
                requiredScalar(request, TrustedRequestHeaders.USER_ROLE));
        String sessionId =
                requiredScalar(request, TrustedRequestHeaders.SESSION_ID);
        String tokenId =
                requiredScalar(request, TrustedRequestHeaders.TOKEN_ID);
        TrustedContextType contextType = contextType(
                requiredScalar(request, TrustedRequestHeaders.CONTEXT_TYPE));
        String tenantId =
                optionalScalar(request, TrustedRequestHeaders.TENANT_ID);
        String tenantCode =
                optionalScalar(request, TrustedRequestHeaders.TENANT_CODE);
        String tenantRole =
                optionalScalar(request, TrustedRequestHeaders.TENANT_ROLE);
        Long tenantContextVersion = optionalPositiveLong(
                request,
                TrustedRequestHeaders.TENANT_CONTEXT_VERSION,
                "租户上下文版本");
        Long memberContextVersion = optionalPositiveLong(
                request,
                TrustedRequestHeaders.MEMBER_CONTEXT_VERSION,
                "成员上下文版本");
        List<String> authorities = authorities(request);

        rejectForbiddenCombination(
                globalRole,
                contextType,
                tenantRole,
                memberContextVersion,
                authorities);

        try {
            return attach(request, TrustedRequestContext.user(
                    source,
                    userId,
                    globalRole,
                    sessionId,
                    tokenId,
                    contextType,
                    tenantId,
                    tenantCode,
                    tenantRole,
                    tenantContextVersion,
                    memberContextVersion,
                    authorities,
                    requestId));
        } catch (IllegalArgumentException exception) {
            throw TrustedRequestContextException.authentication(
                    "可信请求上下文格式无效");
        }
    }

    public static TrustedRequestContext resolved(
            HttpServletRequest request) {
        Object value = request.getAttribute(
                TrustedRequestHeaders.TRUSTED_CONTEXT_ATTRIBUTE);
        return value instanceof TrustedRequestContext
                ? (TrustedRequestContext) value
                : null;
    }

    private TrustedRequestContext attach(
            HttpServletRequest request,
            TrustedRequestContext context) {
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_CONTEXT_ATTRIBUTE,
                context);
        return context;
    }

    private TrustedSource trustedSource(HttpServletRequest request) {
        Object source = request.getAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE);
        if (TrustedRequestHeaders.SOURCE_GATEWAY.equals(source)) {
            return TrustedSource.GATEWAY_USER;
        }
        if (TrustedRequestHeaders.SOURCE_INTERNAL.equals(source)) {
            return TrustedSource.INTERNAL_SERVICE;
        }
        throw TrustedRequestContextException.authentication(
                "请求没有可信来源标记");
    }

    private boolean hasIdentityHeaders(HttpServletRequest request) {
        for (String name : TrustedRequestHeaders.IDENTITY_CONTEXT_HEADERS) {
            if (StringUtils.hasText(request.getHeader(name))) {
                return true;
            }
        }
        return false;
    }

    private String requiredScalar(
            HttpServletRequest request,
            String name) {
        String value = optionalScalar(request, name);
        if (value == null) {
            throw TrustedRequestContextException.authentication(
                    "可信请求上下文字段缺失");
        }
        return value;
    }

    private String optionalScalar(
            HttpServletRequest request,
            String name) {
        Enumeration<String> values = request.getHeaders(name);
        if (values == null) {
            return null;
        }
        String result = null;
        while (values.hasMoreElements()) {
            String value = normalize(values.nextElement());
            if (value == null) {
                continue;
            }
            if (result != null) {
                throw TrustedRequestContextException.authentication(
                        "可信请求上下文包含重复字段");
            }
            result = value;
        }
        return result;
    }

    private Integer role(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0 || parsed > 2) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw TrustedRequestContextException.authentication(
                    "可信请求上下文角色格式错误");
        }
    }

    private Long positiveLong(String value, String label) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw TrustedRequestContextException.authentication(
                    label + "格式错误");
        }
    }

    private Long optionalPositiveLong(
            HttpServletRequest request,
            String name,
            String label) {
        String value = optionalScalar(request, name);
        return value == null ? null : positiveLong(value, label);
    }

    private TrustedContextType contextType(String value) {
        try {
            return TrustedContextType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw TrustedRequestContextException.authentication(
                    "可信请求上下文类型不受支持");
        }
    }

    private List<String> authorities(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders(
                TrustedRequestHeaders.PLATFORM_AUTHORITIES);
        if (values == null) {
            return Collections.emptyList();
        }
        Set<String> authorities = new LinkedHashSet<>();
        while (values.hasMoreElements()) {
            String value = values.nextElement();
            if (!StringUtils.hasText(value)) {
                continue;
            }
            for (String authority : value.split(",")) {
                String normalized = normalize(authority);
                if (normalized != null) {
                    authorities.add(normalized);
                }
            }
        }
        return new ArrayList<>(authorities);
    }

    private void rejectForbiddenCombination(
            Integer globalRole,
            TrustedContextType contextType,
            String tenantRole,
            Long memberContextVersion,
            List<String> authorities) {
        if (contextType == TrustedContextType.PLATFORM
                && globalRole != 0) {
            throw TrustedRequestContextException.permission(
                    "当前身份不能进入平台上下文");
        }
        if (contextType == TrustedContextType.TENANT
                && globalRole == 0) {
            throw TrustedRequestContextException.permission(
                    "平台身份不能伪装为租户成员");
        }
        if (contextType == TrustedContextType.TENANT
                && !authorities.isEmpty()) {
            throw TrustedRequestContextException.permission(
                    "租户成员上下文不能携带平台权限");
        }
        if (contextType == TrustedContextType.PLATFORM_TENANT) {
            if (globalRole != 0) {
                throw TrustedRequestContextException.permission(
                        "当前身份不能进入平台代管上下文");
            }
            if (tenantRole != null || memberContextVersion != null) {
                throw TrustedRequestContextException.permission(
                        "平台代管上下文不能伪造租户成员身份");
            }
            if (authorities.isEmpty()) {
                throw TrustedRequestContextException.permission(
                        "平台代管上下文缺少代管权限");
            }
        }
    }

    private String requestId(HttpServletRequest request) {
        Object current = request.getAttribute(RequestId.REQUEST_ATTRIBUTE);
        if (current instanceof String
                && RequestId.isValid((String) current)) {
            return (String) current;
        }
        String header = request.getHeader(RequestId.HEADER_NAME);
        String resolved = RequestId.isValid(header)
                ? header
                : RequestId.generate();
        request.setAttribute(RequestId.REQUEST_ATTRIBUTE, resolved);
        return resolved;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
