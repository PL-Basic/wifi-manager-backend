package com.plagod.controller;

import com.plagod.audit.Audited;
import com.plagod.audit.AuditDetail;
import com.plagod.audit.AuditTargetId;
import com.plagod.client.TenantContextClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.auth.AuthResultDTO;
import com.plagod.dto.tenant.PlatformTenantContextRequest;
import com.plagod.dto.tenant.TenantContextResolveRequest;
import com.plagod.dto.tenant.TenantContextSwitchRequest;
import com.plagod.exception.ApiStatusException;
import com.plagod.service.AuthSessionService;
import com.plagod.vo.tenant.TenantContextVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import feign.FeignException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
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

    private static final Logger LOGGER =
            LoggerFactory.getLogger(TenantContextController.class);

    private final TenantContextClient tenantContextClient;
    private final AuthSessionService authSessionService;
    private final String internalToken;
    private final TransactionTemplate remoteCallTemplate;

    public TenantContextController(TenantContextClient tenantContextClient,
                                   AuthSessionService authSessionService,
                                   PlatformTransactionManager transactionManager,
                                   @Value("${wifi.internal.token}") String internalToken) {
        this.tenantContextClient = tenantContextClient;
        this.authSessionService = authSessionService;
        this.internalToken = internalToken;
        this.remoteCallTemplate = new TransactionTemplate(transactionManager);
        this.remoteCallTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
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
            targetType = "CONTEXT",
            scope = Audited.Scope.CONTEXT,
            tenantIdSource = Audited.TenantIdSource.REQUEST,
            target = "PLATFORM",
            recordDenied = true,
            recordFailed = true)
    public ApiResponse<AuthResultDTO> returnPlatform(
            @RequestHeader("X-Session-Id") String sessionId,
            @RequestHeader("X-User-Id") Long userId,
            @AuditDetail("globalRole")
            @RequestHeader("X-User-Role") Integer role) {
        requireSuperAdmin(role);
        TenantContextVO context = resolve(userId, role, "PLATFORM", null);
        return ApiResponse.success(
                "已返回平台上下文",
                authSessionService.switchContext(sessionId, userId, role, context));
    }

    @PostMapping("/platform-context/tenants/{tenantId}")
    @Audited(
            action = "auth.platform_tenant_context",
            targetType = "TENANT",
            scope = Audited.Scope.CONTEXT,
            tenantIdSource = Audited.TenantIdSource.REQUEST,
            recordDenied = true,
            recordFailed = true)
    public ApiResponse<AuthResultDTO> enterPlatformTenant(
            @AuditTargetId @PathVariable String tenantId,
            @Valid @RequestBody PlatformTenantContextRequest request,
            @RequestHeader("X-Session-Id") String sessionId,
            @RequestHeader("X-User-Id") Long userId,
            @AuditDetail("globalRole")
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
            response = remoteCallTemplate.execute(status ->
                    tenantContextClient.resolve(internalToken, request));
        } catch (FeignException exception) {
            LOGGER.warn(
                    "tenant context controller resolve failed: status={}, exception={}, contextType={}, tenantIdPresent={}",
                    exception.status(),
                    exception.getClass().getSimpleName(),
                    contextType,
                    tenantId != null);
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
            LOGGER.warn(
                    "tenant context controller resolve failed before HTTP response: exception={}, contextType={}, tenantIdPresent={}",
                    exception.getClass().getSimpleName(),
                    contextType,
                    tenantId != null);
            throw ApiStatusException.serviceUnavailable("租户上下文服务暂时不可用");
        }
        if (response == null || response.getCode() != 200 || response.getData() == null) {
            LOGGER.warn(
                    "tenant context controller resolve returned invalid response: responsePresent={}, code={}, dataPresent={}, contextType={}, tenantIdPresent={}",
                    response != null,
                    response == null ? null : response.getCode(),
                    response != null && response.getData() != null,
                    contextType,
                    tenantId != null);
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
