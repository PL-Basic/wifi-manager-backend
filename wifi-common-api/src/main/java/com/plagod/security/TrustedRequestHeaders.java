package com.plagod.security;

import com.plagod.request.RequestId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Gateway、Servlet Security Starter 与 Feign 共用的可信请求头契约。
 */
public final class TrustedRequestHeaders {

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

    public static final String LEGACY_CONTEXT_VERSION = "X-Context-Version";
    public static final String CLIENT_IP = "X-Client-IP";
    public static final String AUTHORIZATION = "Authorization";
    public static final String COOKIE = "Cookie";

    // 保留已发布字面值，避免滚动部署期间旧消费者读不到来源标记。
    public static final String TRUSTED_SOURCE_ATTRIBUTE =
            "com.plagod.security.TrustedHeaderNames.trustedSource";
    public static final String TRUSTED_CONTEXT_ATTRIBUTE =
            TrustedRequestHeaders.class.getName() + ".trustedContext";
    public static final String SOURCE_GATEWAY = "GATEWAY";
    public static final String SOURCE_INTERNAL = "INTERNAL";

    public static final List<String> IDENTITY_CONTEXT_HEADERS =
            Collections.unmodifiableList(Arrays.asList(
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

    public static final List<String> PROPAGATED_CONTEXT_HEADERS;
    public static final List<String> GATEWAY_STRIPPED_HEADERS;

    static {
        List<String> propagated = new ArrayList<>(IDENTITY_CONTEXT_HEADERS);
        propagated.add(RequestId.HEADER_NAME);
        PROPAGATED_CONTEXT_HEADERS =
                Collections.unmodifiableList(propagated);

        List<String> stripped = new ArrayList<>(IDENTITY_CONTEXT_HEADERS);
        stripped.add(LEGACY_CONTEXT_VERSION);
        stripped.add(GATEWAY_TOKEN);
        stripped.add(INTERNAL_TOKEN);
        stripped.add(CLIENT_IP);
        GATEWAY_STRIPPED_HEADERS =
                Collections.unmodifiableList(stripped);
    }

    private TrustedRequestHeaders() {
    }
}
