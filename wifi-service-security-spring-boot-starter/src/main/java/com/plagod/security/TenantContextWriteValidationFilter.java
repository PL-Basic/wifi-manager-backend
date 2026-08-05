package com.plagod.security;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.TenantContextValidationRequest;
import com.plagod.vo.tenant.TenantContextValidationVO;
import feign.FeignException;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 对租户上下文中的业务写请求执行下游实时授权校验。
 */
public class TenantContextWriteValidationFilter extends OncePerRequestFilter {

    private static final String TENANT_SERVICE = "tenant-service";
    private static final String CONTEXT_TENANT = "TENANT";
    private static final String CONTEXT_PLATFORM_TENANT = "PLATFORM_TENANT";
    private static final String CONTEXT_PLATFORM = "PLATFORM";

    private final TenantContextValidationClient validationClient;
    private final String applicationName;

    public TenantContextWriteValidationFilter(TenantContextValidationClient validationClient,
                                              String applicationName) {
        this.validationClient = validationClient;
        this.applicationName = applicationName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!requiresValidation(request)) {
            chain.doFilter(request, response);
            return;
        }

        TenantContextValidationRequest validationRequest;
        try {
            validationRequest = buildValidationRequest(request);
        } catch (IllegalArgumentException exception) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, exception.getMessage());
            return;
        }

        try {
            ApiResponse<TenantContextValidationVO> result =
                    validationClient.validate(validationRequest);
            int rejectedStatus = rejectedStatus(result);
            if (rejectedStatus != 0) {
                reject(
                        response,
                        rejectedStatus,
                        rejectedStatus == HttpServletResponse.SC_SERVICE_UNAVAILABLE
                                ? "租户上下文校验服务返回无效结果"
                                : "租户上下文已失效");
                return;
            }
        } catch (FeignException exception) {
            int status = exception.status();
            if (status == HttpServletResponse.SC_UNAUTHORIZED
                    || status == HttpServletResponse.SC_FORBIDDEN) {
                reject(response, status, "租户上下文已失效");
            } else {
                reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "租户上下文校验服务暂时不可用");
            }
            return;
        } catch (RuntimeException exception) {
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "租户上下文校验服务暂时不可用");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean requiresValidation(HttpServletRequest request) {
        if (TENANT_SERVICE.equalsIgnoreCase(applicationName)
                || isSafeMethod(request.getMethod())
                || isExcludedPath(request.getRequestURI())
                || !isTrustedRequest(request)) {
            return false;
        }

        String contextType = normalize(request.getHeader(TrustedHeaderNames.CONTEXT_TYPE));
        if (contextType == null
                && TrustedHeaderNames.SOURCE_INTERNAL.equals(
                request.getAttribute(TrustedHeaderNames.TRUSTED_SOURCE_ATTRIBUTE))) {
            // 后台内部任务没有浏览器租户上下文，由其专用业务凭据和资源所有权校验负责。
            return false;
        }
        // PLATFORM 写由平台角色和专用接口授权；其余普通业务写必须携带租户上下文。
        // 缺失或未知上下文也进入校验并以 401 拒绝，不能静默绕过。
        return !CONTEXT_PLATFORM.equals(contextType);
    }

    private boolean isSafeMethod(String method) {
        return "GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
    }

    private boolean isExcludedPath(String path) {
        return path != null
                && (path.equals("/auth")
                || path.startsWith("/auth/")
                || path.equals("/internal/auth")
                || path.startsWith("/internal/auth/")
                || path.startsWith("/payment/callbacks/"));
    }

    private boolean isTrustedRequest(HttpServletRequest request) {
        Object source = request.getAttribute(TrustedHeaderNames.TRUSTED_SOURCE_ATTRIBUTE);
        return TrustedHeaderNames.SOURCE_GATEWAY.equals(source)
                || TrustedHeaderNames.SOURCE_INTERNAL.equals(source);
    }

    private TenantContextValidationRequest buildValidationRequest(HttpServletRequest request) {
        TenantContextValidationRequest validationRequest = new TenantContextValidationRequest();
        validationRequest.setUserId(requiredHeader(request, TrustedHeaderNames.USER_ID));
        validationRequest.setGlobalRole(integerHeader(request, TrustedHeaderNames.USER_ROLE));
        String contextType = requiredHeader(request, TrustedHeaderNames.CONTEXT_TYPE);
        validationRequest.setContextType(contextType);
        validationRequest.setTenantId(requiredHeader(request, TrustedHeaderNames.TENANT_ID));
        validationRequest.setTenantCode(requiredHeader(request, TrustedHeaderNames.TENANT_CODE));
        validationRequest.setTenantRole(CONTEXT_TENANT.equals(contextType)
                ? requiredHeader(request, TrustedHeaderNames.TENANT_ROLE)
                : optionalHeader(request, TrustedHeaderNames.TENANT_ROLE));
        validationRequest.setContextVersion(
                longHeader(request, TrustedHeaderNames.TENANT_CONTEXT_VERSION, true));
        validationRequest.setMemberContextVersion(
                longHeader(
                        request,
                        TrustedHeaderNames.MEMBER_CONTEXT_VERSION,
                        CONTEXT_TENANT.equals(contextType)));
        validationRequest.setAuthorities(authorities(request));
        validationRequest.setWriteRequest(true);
        validationRequest.setLegacyToken(false);
        return validationRequest;
    }

    private int rejectedStatus(ApiResponse<TenantContextValidationVO> result) {
        if (result == null) {
            return HttpServletResponse.SC_SERVICE_UNAVAILABLE;
        }
        if (result.getCode() == HttpServletResponse.SC_UNAUTHORIZED) {
            return HttpServletResponse.SC_UNAUTHORIZED;
        }
        if (result.getCode() == HttpServletResponse.SC_FORBIDDEN
                || result.getCode() == HttpServletResponse.SC_NOT_FOUND) {
            return HttpServletResponse.SC_FORBIDDEN;
        }
        if (result.getCode() != HttpServletResponse.SC_OK
                || result.getData() == null) {
            return HttpServletResponse.SC_SERVICE_UNAVAILABLE;
        }
        return Boolean.TRUE.equals(result.getData().getAllowed())
                ? 0
                : HttpServletResponse.SC_FORBIDDEN;
    }

    private String requiredHeader(HttpServletRequest request, String name) {
        String value = optionalHeader(request, name);
        if (value == null) {
            throw new IllegalArgumentException("可信租户上下文缺少 " + name);
        }
        return value;
    }

    private String optionalHeader(HttpServletRequest request, String name) {
        return normalize(request.getHeader(name));
    }

    private Integer integerHeader(HttpServletRequest request, String name) {
        String value = requiredHeader(request, name);
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("可信租户上下文中的 " + name + " 格式错误");
        }
    }

    private Long longHeader(HttpServletRequest request, String name, boolean required) {
        String value = optionalHeader(request, name);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("可信租户上下文缺少 " + name);
            }
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("可信租户上下文中的 " + name + " 格式错误");
        }
    }

    private List<String> authorities(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders(TrustedHeaderNames.PLATFORM_AUTHORITIES);
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
                if (StringUtils.hasText(authority)) {
                    authorities.add(authority.trim());
                }
            }
        }
        return new ArrayList<>(authorities);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        String safeMessage = message == null
                ? "租户上下文校验失败"
                : message.replace("\\", "\\\\").replace("\"", "\\\"");
        byte[] body = String.format(
                Locale.ROOT,
                "{\"code\":%d,\"message\":\"%s\",\"data\":null}",
                status,
                safeMessage).getBytes(StandardCharsets.UTF_8);
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json; charset=utf-8");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}
