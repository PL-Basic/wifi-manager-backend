package com.plagod.security;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.TenantContextValidationRequest;
import com.plagod.exception.ApiErrorKey;
import com.plagod.request.RequestId;
import com.plagod.vo.tenant.TenantContextValidationVO;
import feign.FeignException;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 对租户上下文中的业务写请求执行下游实时授权校验。
 */
public class TenantContextWriteValidationFilter extends OncePerRequestFilter {

    private static final String TENANT_SERVICE = "tenant-service";

    private final TenantContextValidationClient validationClient;
    private final TrustedRequestContextResolver contextResolver;
    private final String applicationName;

    public TenantContextWriteValidationFilter(TenantContextValidationClient validationClient,
                                              String applicationName) {
        this(
                validationClient,
                new TrustedRequestContextResolver(),
                applicationName);
    }

    public TenantContextWriteValidationFilter(
            TenantContextValidationClient validationClient,
            TrustedRequestContextResolver contextResolver,
            String applicationName) {
        this.validationClient = validationClient;
        this.contextResolver = contextResolver;
        this.applicationName = applicationName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (skipsValidation(request)) {
            chain.doFilter(request, response);
            return;
        }

        TrustedRequestContext context;
        try {
            context = contextResolver.resolve(request);
        } catch (TrustedRequestContextException exception) {
            reject(
                    request,
                    response,
                    exception.getHttpStatus(),
                    exception.getMessage(),
                    exception.getErrorKey().value());
            return;
        }
        if (context == null) {
            reject(
                    request,
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "可信用户上下文缺失");
            return;
        }
        if (!requiresValidation(context)) {
            chain.doFilter(request, response);
            return;
        }

        TenantContextValidationRequest validationRequest;
        try {
            validationRequest = buildValidationRequest(context);
        } catch (IllegalArgumentException exception) {
            reject(
                    request,
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    exception.getMessage());
            return;
        }

        try {
            ApiResponse<TenantContextValidationVO> result =
                    validationClient.validate(validationRequest);
            int rejectedStatus = rejectedStatus(result);
            if (rejectedStatus != 0) {
                reject(
                        request,
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
                reject(request, response, status, "租户上下文已失效");
            } else {
                reject(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "租户上下文校验服务暂时不可用");
            }
            return;
        } catch (RuntimeException exception) {
            reject(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "租户上下文校验服务暂时不可用");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean skipsValidation(HttpServletRequest request) {
        if (TENANT_SERVICE.equalsIgnoreCase(applicationName)
                || isSafeMethod(request.getMethod())
                || isExcludedPath(request.getRequestURI())
                || !isTrustedRequest(request)) {
            return true;
        }
        return false;
    }

    private boolean requiresValidation(TrustedRequestContext context) {
        if (!context.hasUserActor()
                && context.getTrustedSource()
                == TrustedSource.INTERNAL_SERVICE) {
            // 后台内部任务没有浏览器租户上下文，由其专用业务凭据和资源所有权校验负责。
            return false;
        }
        // PLATFORM 写由平台角色和专用接口授权；其余普通业务写必须携带租户上下文。
        return context.getContextType() != TrustedContextType.PLATFORM;
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
        Object source = request.getAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE);
        return TrustedRequestHeaders.SOURCE_GATEWAY.equals(source)
                || TrustedRequestHeaders.SOURCE_INTERNAL.equals(source);
    }

    private TenantContextValidationRequest buildValidationRequest(
            TrustedRequestContext context) {
        TenantContextValidationRequest validationRequest = new TenantContextValidationRequest();
        validationRequest.setUserId(String.valueOf(context.getUserId()));
        validationRequest.setGlobalRole(context.getGlobalRole());
        validationRequest.setContextType(context.getContextType().name());
        validationRequest.setTenantId(context.getTenantId());
        validationRequest.setTenantCode(context.getTenantCode());
        validationRequest.setTenantRole(context.getTenantRole());
        validationRequest.setContextVersion(
                context.getTenantContextVersion());
        validationRequest.setMemberContextVersion(
                context.getMemberContextVersion());
        validationRequest.setAuthorities(
                context.getPlatformAuthorities());
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

    private void reject(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String message) throws IOException {
        String errorKey = status == HttpServletResponse.SC_UNAUTHORIZED
                ? ApiErrorKey.SESSION_EXPIRED.value()
                : ApiErrorKey.defaultForHttpStatus(status).value();
        reject(request, response, status, message, errorKey);
    }

    private void reject(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String message,
            String errorKey) throws IOException {
        String safeMessage = message == null
                ? "租户上下文校验失败"
                : message.replace("\\", "\\\\").replace("\"", "\\\"");
        String requestId = requestId(request);
        byte[] body = String.format(
                Locale.ROOT,
                "{\"code\":%d,\"message\":\"%s\",\"data\":null,"
                        + "\"errorKey\":\"%s\",\"requestId\":\"%s\"}",
                status,
                safeMessage,
                errorKey,
                requestId).getBytes(StandardCharsets.UTF_8);
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json; charset=utf-8");
        response.setHeader(RequestId.HEADER_NAME, requestId);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private String requestId(HttpServletRequest request) {
        Object current = request.getAttribute(RequestId.REQUEST_ATTRIBUTE);
        if (current instanceof String
                && RequestId.isValid((String) current)) {
            return (String) current;
        }
        String generated = RequestId.generate();
        request.setAttribute(RequestId.REQUEST_ATTRIBUTE, generated);
        return generated;
    }
}
