package com.plagod.security;

import com.plagod.request.RequestId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustedRequestContextTest {

    private static final String REQUEST_ID = "request-id-00000001";

    @Test
    void headerContractHasOneRequestIdAwarePropagationList() {
        assertEquals("X-User-Id", TrustedRequestHeaders.USER_ID);
        assertEquals("X-Tenant-Id", TrustedRequestHeaders.TENANT_ID);
        assertEquals("X-Internal-Token",
                TrustedRequestHeaders.INTERNAL_TOKEN);
        assertEquals(
                "com.plagod.security.TrustedHeaderNames.trustedSource",
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE);
        assertTrue(TrustedRequestHeaders.PROPAGATED_CONTEXT_HEADERS
                .contains(RequestId.HEADER_NAME));
        assertFalse(TrustedRequestHeaders.GATEWAY_STRIPPED_HEADERS
                .contains(RequestId.HEADER_NAME));
        assertTrue(TrustedRequestHeaders.GATEWAY_STRIPPED_HEADERS
                .contains(TrustedRequestHeaders.LEGACY_CONTEXT_VERSION));
    }

    @Test
    void platformContextIsImmutableAndHasNoTenantIdentity() {
        List<String> authorities =
                new ArrayList<>(Arrays.asList("TENANT_MANAGE", "AUDIT_READ"));

        TrustedRequestContext context = TrustedRequestContext.user(
                TrustedSource.GATEWAY_USER,
                7L,
                0,
                "session-00000001",
                "token-id-00000001",
                TrustedContextType.PLATFORM,
                null,
                null,
                null,
                null,
                null,
                authorities,
                REQUEST_ID);
        authorities.clear();

        assertEquals(
                Arrays.asList("AUDIT_READ", "TENANT_MANAGE"),
                context.getPlatformAuthorities());
        assertNull(context.getTenantId());
        assertThrows(
                UnsupportedOperationException.class,
                () -> context.getPlatformAuthorities().add("FORGED"));
    }

    @Test
    void tenantAndPlatformTenantRemainDifferentContexts() {
        TrustedRequestContext tenant = TrustedRequestContext.user(
                TrustedSource.GATEWAY_USER,
                17L,
                1,
                "session-00000001",
                "token-id-00000001",
                TrustedContextType.TENANT,
                "3",
                "tenant-a",
                "TENANT_ADMIN",
                4L,
                5L,
                Collections.emptyList(),
                REQUEST_ID);
        TrustedRequestContext managed = TrustedRequestContext.user(
                TrustedSource.INTERNAL_SERVICE,
                1L,
                0,
                "session-00000002",
                "token-id-00000002",
                TrustedContextType.PLATFORM_TENANT,
                "3",
                "tenant-a",
                null,
                4L,
                null,
                Collections.singletonList("TENANT_MANAGE"),
                REQUEST_ID);

        assertEquals(TrustedContextType.TENANT, tenant.getContextType());
        assertEquals("TENANT_ADMIN", tenant.getTenantRole());
        assertEquals(
                TrustedContextType.PLATFORM_TENANT,
                managed.getContextType());
        assertNull(managed.getTenantRole());
        assertEquals(
                TrustedSource.INTERNAL_SERVICE,
                managed.getTrustedSource());
    }

    @Test
    void illegalUserContextCombinationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                TrustedRequestContext.user(
                        TrustedSource.GATEWAY_USER,
                        1L,
                        0,
                        "session-00000001",
                        "token-id-00000001",
                        TrustedContextType.TENANT,
                        "3",
                        "tenant-a",
                        "TENANT_ADMIN",
                        4L,
                        5L,
                        Collections.emptyList(),
                        REQUEST_ID));
        assertThrows(IllegalArgumentException.class, () ->
                TrustedRequestContext.user(
                        TrustedSource.GATEWAY_USER,
                        1L,
                        0,
                        "session-00000001",
                        "token-id-00000001",
                        TrustedContextType.PLATFORM_TENANT,
                        "3",
                        "tenant-a",
                        "TENANT_ADMIN",
                        4L,
                        5L,
                        Collections.singletonList("TENANT_MANAGE"),
                        REQUEST_ID));
        assertThrows(IllegalArgumentException.class, () ->
                TrustedRequestContext.user(
                        TrustedSource.GATEWAY_USER,
                        1L,
                        0,
                        "session-00000001",
                        "token-id-00000001",
                        TrustedContextType.PLATFORM,
                        "3",
                        "tenant-a",
                        null,
                        4L,
                        null,
                        Collections.singletonList("TENANT_MANAGE"),
                        REQUEST_ID));
    }

    @Test
    void serviceAndDeviceSourcesCannotBecomeBrowserActors() {
        TrustedRequestContext internal =
                TrustedRequestContext.internalService(REQUEST_ID);
        TrustedRequestContext scheduled =
                TrustedRequestContext.scheduledService("3", REQUEST_ID);
        TrustedRequestContext device =
                TrustedRequestContext.deviceEvent(
                        "3",
                        "tenant-a",
                        REQUEST_ID);

        assertFalse(internal.hasUserActor());
        assertEquals(
                TrustedSource.SCHEDULED_SERVICE,
                scheduled.getTrustedSource());
        assertEquals("3", scheduled.getTenantId());
        assertEquals(
                TrustedSource.DEVICE_EVENT,
                device.getTrustedSource());
        assertEquals("tenant-a", device.getTenantCode());
    }
}
