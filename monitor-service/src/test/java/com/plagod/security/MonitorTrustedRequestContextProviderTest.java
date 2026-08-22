package com.plagod.security;

import com.plagod.exception.ApiErrorKey;
import com.plagod.exception.ApiStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonitorTrustedRequestContextProviderTest {

    private TrustedRequestContextResolver contextResolver;
    private MonitorTrustedRequestContextProvider contextProvider;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        contextResolver = mock(TrustedRequestContextResolver.class);
        contextProvider = new MonitorTrustedRequestContextProvider(
                contextResolver);
        request = new MockHttpServletRequest();
    }

    @Test
    void mapsAuthenticationFailureToApiStatus401() {
        when(contextResolver.resolve(request)).thenThrow(
                TrustedRequestContextException.authentication(
                        "可信请求上下文字段缺失"));

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> contextProvider.resolve(request));

        assertEquals(401, exception.getHttpStatus());
        assertEquals(
                ApiErrorKey.AUTHENTICATION_REQUIRED.value(),
                exception.getErrorKey());
        assertEquals("可信请求上下文字段缺失", exception.getMessage());
    }

    @Test
    void mapsPermissionFailureToApiStatus403() {
        when(contextResolver.resolve(request)).thenThrow(
                TrustedRequestContextException.permission(
                        "平台身份不能伪装为租户成员"));

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> contextProvider.resolve(request));

        assertEquals(403, exception.getHttpStatus());
        assertEquals(
                ApiErrorKey.PERMISSION_DENIED.value(),
                exception.getErrorKey());
        assertEquals("平台身份不能伪装为租户成员", exception.getMessage());
    }

    @Test
    void returnsLegalContextWithoutReinterpretingHeaders() {
        TrustedRequestContext context = TrustedRequestContext.user(
                TrustedSource.GATEWAY_USER,
                7L,
                1,
                "session-provider",
                "token-provider",
                TrustedContextType.TENANT,
                "11",
                "tenant-a",
                "TENANT_ADMIN",
                1L,
                1L,
                Collections.emptyList(),
                "request-provider");
        when(contextResolver.resolve(request)).thenReturn(context);

        assertSame(context, contextProvider.resolve(request));
    }
}
