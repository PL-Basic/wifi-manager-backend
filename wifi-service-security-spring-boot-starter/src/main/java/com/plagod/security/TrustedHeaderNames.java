package com.plagod.security;

import java.util.List;

/**
 * @deprecated 请直接使用 {@link TrustedRequestHeaders}。
 */
@Deprecated
public final class TrustedHeaderNames {

    public static final String GATEWAY_TOKEN =
            TrustedRequestHeaders.GATEWAY_TOKEN;
    public static final String INTERNAL_TOKEN =
            TrustedRequestHeaders.INTERNAL_TOKEN;

    public static final String USER_ID = TrustedRequestHeaders.USER_ID;
    public static final String USER_NAME = TrustedRequestHeaders.USER_NAME;
    public static final String USER_ROLE = TrustedRequestHeaders.USER_ROLE;
    public static final String SESSION_ID = TrustedRequestHeaders.SESSION_ID;
    public static final String TOKEN_ID = TrustedRequestHeaders.TOKEN_ID;
    public static final String CONTEXT_TYPE =
            TrustedRequestHeaders.CONTEXT_TYPE;
    public static final String TENANT_ID = TrustedRequestHeaders.TENANT_ID;
    public static final String TENANT_CODE = TrustedRequestHeaders.TENANT_CODE;
    public static final String TENANT_ROLE = TrustedRequestHeaders.TENANT_ROLE;
    public static final String TENANT_CONTEXT_VERSION =
            TrustedRequestHeaders.TENANT_CONTEXT_VERSION;
    public static final String MEMBER_CONTEXT_VERSION =
            TrustedRequestHeaders.MEMBER_CONTEXT_VERSION;
    public static final String PLATFORM_AUTHORITIES =
            TrustedRequestHeaders.PLATFORM_AUTHORITIES;

    public static final String AUTHORIZATION =
            TrustedRequestHeaders.AUTHORIZATION;
    public static final String COOKIE = TrustedRequestHeaders.COOKIE;

    public static final String TRUSTED_SOURCE_ATTRIBUTE =
            TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE;
    public static final String SOURCE_GATEWAY =
            TrustedRequestHeaders.SOURCE_GATEWAY;
    public static final String SOURCE_INTERNAL =
            TrustedRequestHeaders.SOURCE_INTERNAL;

    public static final List<String> PROPAGATED_CONTEXT_HEADERS =
            TrustedRequestHeaders.PROPAGATED_CONTEXT_HEADERS;

    private TrustedHeaderNames() {
    }
}
