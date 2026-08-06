package com.plagod.controller;

import com.plagod.audit.Audited;
import com.plagod.client.TenantContextClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.auth.AuthResultDTO;
import com.plagod.dto.tenant.PlatformTenantContextRequest;
import com.plagod.dto.tenant.TenantContextResolveRequest;
import com.plagod.dto.tenant.TenantContextSwitchRequest;
import com.plagod.exception.ApiStatusException;
import com.plagod.service.AuthSessionService;
import com.plagod.vo.tenant.TenantContextVO;
import org.springframework.beans.factory.annotation.Value;
import feign.FeignException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/auth")
public class TenantContextController {

    private final TenantContextClient tenantContextClient;
    private final AuthSessionService authSessionService;
    private final String internalToken;

    public TenantContextController(TenantContextClient tenantContextClient,
                                   AuthSessionService authSessionService,
                                   @Value("${wifi.internal.token}") String internalToken) {
        this.tenantContextClient = tenantContextClient;
        this.authSessionService = authSessionService;
        this.internalToken = internalToken;
    }

    @PostMapping("/tenant-context/switch")
    public ApiResponse<AuthResultDTO> switchTenant(
            @RequestHeader("X-Session-Id") String sessionId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") Integer role,
            @Valid @RequestBody TenantContextSwitchRequest request) {
        TenantContextVO context = resolve(userId, role, "TENANT", request.getTenantId());
        return ApiResponse.success(
                "租户上下文切换成功",
                authSessionService.switchContext(sessionId, userId, role, context));
    }

    @PostMapping("/platform-context")
    @Audited(
            action = "auth.platform_context",
            target = "PLATFORM",
            includeArgs = false,
            includeResult = false)
    public ApiResponse<AuthResultDTO> returnPlatform(
            @RequestHeader("X-Session-Id") String sessionId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") Integer role) {
        requireSuperAdmin(role);
        TenantContextVO context = resolve(userId, role, "PLATFORM", null);
        return ApiResponse.success(
                "已返回平台上下文",
                authSessionService.switchContext(sessionId, userId, role, context));
    }

    @PostMapping("/platform-context/tenants/{tenantId}")
    @Audited(action = "auth.platform_tenant_context", includeResult = false)
    public ApiResponse<AuthResultDTO> enterPlatformTenant(
            @PathVariable String tenantId,
            @Valid @RequestBody PlatformTenantContextRequest request,
            @RequestHeader("X-Session-Id") String sessionId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") Integer role) {
        requireSuperAdmin(role);
        if (request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("进入租户的原因不能为空");
        }
        TenantContextVO context = resolve(userId, role, "PLATFORM_TENANT", tenantId);
        return ApiResponse.success(
                "已进入代管租户上下文",
                authSessionService.switchContext(sessionId, userId, role, context));
    }

    private TenantContextVO resolve(Long userId, Integer role, String contextType, String tenantId) {
        TenantContextResolveRequest request = new TenantContextResolveRequest();
        request.setUserId(String.valueOf(userId));
        request.setGlobalRole(role);
        request.setContextType(contextType);
        request.setTenantId(tenantId);
        ApiResponse<TenantContextVO> response;
        try {
            response = tenantContextClient.resolve(internalToken, request);
        } catch (FeignException exception) {
            if (exception.status() == 400) {
                throw new IllegalArgumentException("租户上下文请求无效");
            }
            if (exception.status() == 401) {
                throw new ApiStatusException(401, 401, "租户上下文已变化");
            }
            if (exception.status() == 403) {
                throw ApiStatusException.forbidden("无权使用目标租户上下文");
            }
            if (exception.status() == 404) {
                throw ApiStatusException.notFound("目标租户或用户不存在");
            }
            throw ApiStatusException.serviceUnavailable("租户上下文服务暂时不可用");
        } catch (RuntimeException exception) {
            throw ApiStatusException.serviceUnavailable("租户上下文服务暂时不可用");
        }
        if (response == null || response.getCode() != 200 || response.getData() == null) {
            throw ApiStatusException.serviceUnavailable("租户上下文服务返回无效结果");
        }
        return response.getData();
    }

    private void requireSuperAdmin(Integer role) {
        if (!Integer.valueOf(0).equals(role)) {
            throw ApiStatusException.forbidden("仅超级管理员可以切换平台上下文");
        }
    }
}
