package com.plagod.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Servlet 微服务之间允许传递的可信请求头。
 */
public final class TrustedHeaderNames {

    public static final String GATEWAY_TOKEN = "X-Gateway-Token";
    public static final String INTERNAL_TOKEN = "X-Internal-Token";

    public static final String USER_ID = "X-User-Id";
    public static final String USER_NAME = "X-User-Name";
    public static final String USER_ROLE = "X-User-Role";
    public static final String SESSION_ID = "X-Session-Id";
    public static final String TOKEN_ID = "X-Token-Id";
    public static final String CONTEXT_TYPE = "X-Context-Type";
    public static final String TENANT_ID = "X-Tenant-Id";
    public static final String TENANT_CODE = "X-Tenant-Code";
    public static final String TENANT_ROLE = "X-Tenant-Role";
    public static final String TENANT_CONTEXT_VERSION = "X-Tenant-Context-Version";
    public static final String MEMBER_CONTEXT_VERSION = "X-Member-Context-Version";
    public static final String PLATFORM_AUTHORITIES = "X-Platform-Authorities";

    public static final String AUTHORIZATION = "Authorization";
    public static final String COOKIE = "Cookie";

    public static final String TRUSTED_SOURCE_ATTRIBUTE =
            TrustedHeaderNames.class.getName() + ".trustedSource";
    public static final String SOURCE_GATEWAY = "GATEWAY";
    public static final String SOURCE_INTERNAL = "INTERNAL";

    public static final List<String> PROPAGATED_CONTEXT_HEADERS = Collections.unmodifiableList(
            Arrays.asList(
                    USER_ID,
                    USER_NAME,
                    USER_ROLE,
                    SESSION_ID,
                    TOKEN_ID,
                    CONTEXT_TYPE,
                    TENANT_ID,
                    TENANT_CODE,
                    TENANT_ROLE,
                    TENANT_CONTEXT_VERSION,
                    MEMBER_CONTEXT_VERSION,
                    PLATFORM_AUTHORITIES));

    private TrustedHeaderNames() {
    }
}
