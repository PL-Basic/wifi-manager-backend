package com.plagod.security;

import com.plagod.request.RequestId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 已验证请求身份、工作区与关联 ID 的不可变快照。
 */
public final class TrustedRequestContext {

    private static final Pattern SAFE_NAME =
            Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    private static final Pattern SAFE_IDENTIFIER =
            Pattern.compile("^[A-Za-z0-9_-]{1,128}$");
    private static final Pattern POSITIVE_ID =
            Pattern.compile("^[1-9]\\d*$");

    private final TrustedSource trustedSource;
    private final Long userId;
    private final Integer globalRole;
    private final String sessionId;
    private final String tokenId;
    private final TrustedContextType contextType;
    private final String tenantId;
    private final String tenantCode;
    private final String tenantRole;
    private final Long tenantContextVersion;
    private final Long memberContextVersion;
    private final List<String> platformAuthorities;
    private final String requestId;

    private TrustedRequestContext(
            TrustedSource trustedSource,
            Long userId,
            Integer globalRole,
            String sessionId,
            String tokenId,
            TrustedContextType contextType,
            String tenantId,
            String tenantCode,
            String tenantRole,
            Long tenantContextVersion,
            Long memberContextVersion,
            Collection<String> platformAuthorities,
            String requestId) {
        this.trustedSource = required(trustedSource, "可信来源");
        this.userId = userId;
        this.globalRole = globalRole;
        this.sessionId = trimToNull(sessionId);
        this.tokenId = trimToNull(tokenId);
        this.contextType = contextType;
        this.tenantId = trimToNull(tenantId);
        this.tenantCode = trimToNull(tenantCode);
        this.tenantRole = trimToNull(tenantRole);
        this.tenantContextVersion = tenantContextVersion;
        this.memberContextVersion = memberContextVersion;
        this.platformAuthorities = immutableAuthorities(platformAuthorities);
        this.requestId = requireRequestId(requestId);
        validate();
    }

    public static TrustedRequestContext user(
            TrustedSource trustedSource,
            Long userId,
            Integer globalRole,
            String sessionId,
            String tokenId,
            TrustedContextType contextType,
            String tenantId,
            String tenantCode,
            String tenantRole,
            Long tenantContextVersion,
            Long memberContextVersion,
            Collection<String> platformAuthorities,
            String requestId) {
        return new TrustedRequestContext(
                trustedSource,
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
                platformAuthorities,
                requestId);
    }

    public static TrustedRequestContext internalService(String requestId) {
        return service(
                TrustedSource.INTERNAL_SERVICE,
                null,
                null,
                requestId);
    }

    public static TrustedRequestContext scheduledService(
            String persistedTenantId,
            String requestId) {
        return service(
                TrustedSource.SCHEDULED_SERVICE,
                persistedTenantId,
                null,
                requestId);
    }

    public static TrustedRequestContext deviceEvent(
            String resolvedTenantId,
            String resolvedTenantCode,
            String requestId) {
        return service(
                TrustedSource.DEVICE_EVENT,
                resolvedTenantId,
                resolvedTenantCode,
                requestId);
    }

    private static TrustedRequestContext service(
            TrustedSource source,
            String tenantId,
            String tenantCode,
            String requestId) {
        return new TrustedRequestContext(
                source,
                null,
                null,
                null,
                null,
                null,
                tenantId,
                tenantCode,
                null,
                null,
                null,
                Collections.emptyList(),
                requestId);
    }

    private void validate() {
        if (trustedSource == TrustedSource.SCHEDULED_SERVICE
                || trustedSource == TrustedSource.DEVICE_EVENT) {
            validateNonUserService();
            return;
        }

        if (trustedSource == TrustedSource.INTERNAL_SERVICE
                && userId == null
                && contextType == null) {
            requireAbsentUserFields();
            requireAbsentTenantFields();
            return;
        }

        if (trustedSource != TrustedSource.GATEWAY_USER
                && trustedSource != TrustedSource.INTERNAL_SERVICE) {
            throw new IllegalArgumentException("当前可信来源不能承载用户上下文");
        }
        requirePositive(userId, "用户ID");
        requireRole(globalRole);
        requireSafeIdentifier(sessionId, "会话ID");
        requireSafeIdentifier(tokenId, "Token ID");
        required(contextType, "上下文类型");

        if (contextType == TrustedContextType.PLATFORM) {
            requireRoleEquals(0, "PLATFORM 只允许平台用户");
            requireAbsentTenantFields();
            return;
        }
        if (contextType == TrustedContextType.TENANT) {
            if (globalRole == 0) {
                throw new IllegalArgumentException(
                        "平台用户不能伪装为租户成员上下文");
            }
            requireTenantIdentity(true);
            if (!platformAuthorities.isEmpty()) {
                throw new IllegalArgumentException(
                        "TENANT 上下文不能携带平台权限");
            }
            return;
        }
        if (contextType == TrustedContextType.PLATFORM_TENANT) {
            requireRoleEquals(0, "PLATFORM_TENANT 只允许平台用户");
            requireTenantIdentity(false);
            if (tenantRole != null || memberContextVersion != null) {
                throw new IllegalArgumentException(
                        "PLATFORM_TENANT 不能伪造租户成员身份");
            }
            if (platformAuthorities.isEmpty()) {
                throw new IllegalArgumentException(
                        "PLATFORM_TENANT 必须携带平台代管权限");
            }
        }
    }

    private void validateNonUserService() {
        requireAbsentUserFields();
        if (contextType != null
                || tenantRole != null
                || tenantContextVersion != null
                || memberContextVersion != null
                || !platformAuthorities.isEmpty()) {
            throw new IllegalArgumentException(
                    "服务或设备身份不能伪造用户工作区上下文");
        }
        requirePositiveIdText(tenantId, "持久化租户ID");
        if (trustedSource == TrustedSource.DEVICE_EVENT) {
            requireText(tenantCode, "设备事件租户编码");
        } else if (tenantCode != null) {
            throw new IllegalArgumentException(
                    "后台任务不能从请求构造租户编码");
        }
    }

    private void requireTenantIdentity(boolean memberRequired) {
        requirePositiveIdText(tenantId, "租户ID");
        requireText(tenantCode, "租户编码");
        requirePositive(tenantContextVersion, "租户上下文版本");
        if (memberRequired) {
            requireSafeName(tenantRole, "租户角色");
            requirePositive(memberContextVersion, "成员上下文版本");
        }
    }

    private void requireAbsentUserFields() {
        if (userId != null
                || globalRole != null
                || sessionId != null
                || tokenId != null) {
            throw new IllegalArgumentException(
                    "服务或设备身份不能伪造浏览器用户");
        }
    }

    private void requireAbsentTenantFields() {
        if (tenantId != null
                || tenantCode != null
                || tenantRole != null
                || tenantContextVersion != null
                || memberContextVersion != null) {
            throw new IllegalArgumentException(
                    "当前上下文不能携带租户成员字段");
        }
    }

    private void requireRoleEquals(int expected, String message) {
        if (globalRole == null || globalRole != expected) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireRole(Integer value) {
        if (value == null || value < 0 || value > 2) {
            throw new IllegalArgumentException("全局角色必须在0到2之间");
        }
    }

    private static void requirePositive(Long value, String label) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(label + "必须大于0");
        }
    }

    private static void requirePositiveIdText(String value, String label) {
        if (value == null || !POSITIVE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(label + "必须是大于0的整数");
        }
        try {
            Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + "超出64位整数范围");
        }
    }

    private static void requireSafeIdentifier(String value, String label) {
        if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(label + "格式错误");
        }
    }

    private static void requireSafeName(String value, String label) {
        if (value == null || !SAFE_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(label + "格式错误");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }

    private static String requireRequestId(String value) {
        String normalized = trimToNull(value);
        if (!RequestId.isValid(normalized)) {
            throw new IllegalArgumentException("requestId 格式错误");
        }
        return normalized;
    }

    private static List<String> immutableAuthorities(
            Collection<String> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return Collections.emptyList();
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String authority : authorities) {
            String value = trimToNull(authority);
            requireSafeName(value, "平台权限");
            normalized.add(value);
        }
        return Collections.unmodifiableList(
                new ArrayList<>(normalized));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static <T> T required(T value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value;
    }

    public TrustedSource getTrustedSource() {
        return trustedSource;
    }

    public Long getUserId() {
        return userId;
    }

    public Integer getGlobalRole() {
        return globalRole;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getTokenId() {
        return tokenId;
    }

    public TrustedContextType getContextType() {
        return contextType;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public String getTenantRole() {
        return tenantRole;
    }

    public Long getTenantContextVersion() {
        return tenantContextVersion;
    }

    public Long getMemberContextVersion() {
        return memberContextVersion;
    }

    public List<String> getPlatformAuthorities() {
        return platformAuthorities;
    }

    public String getRequestId() {
        return requestId;
    }

    public boolean hasUserActor() {
        return userId != null;
    }
}
