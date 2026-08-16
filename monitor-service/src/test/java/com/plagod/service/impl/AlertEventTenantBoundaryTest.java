package com.plagod.service.impl;

import com.plagod.entity.monitor.AlertEvent;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.AlertEventMapper;
import com.plagod.security.MonitorTenantScope;
import com.plagod.security.TrustedContextType;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.TrustedSource;
import com.plagod.vo.monitor.AlertEventVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertEventTenantBoundaryTest {

    private static final Long TENANT_A = 11L;
    private static final Long ALERT_A = 101L;
    private static final Long ALERT_B = 202L;

    @Mock
    private AlertEventMapper alertEventMapper;

    private AlertEventServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AlertEventServiceImpl();
        ReflectionTestUtils.setField(
                service,
                "alertEventMapper",
                alertEventMapper);
        ReflectionTestUtils.setField(
                service,
                "tenantScope",
                new MonitorTenantScope());
    }

    @Test
    void tenantAReadsOwnedAlert() {
        AlertEvent entity = alert(ALERT_A, TENANT_A);
        when(alertEventMapper.selectByIdAndTenant(TENANT_A, ALERT_A))
                .thenReturn(entity);

        AlertEventVO result = service.getAlert(tenantContext(), ALERT_A);

        assertEquals(ALERT_A, result.getId());
        verify(alertEventMapper).selectByIdAndTenant(TENANT_A, ALERT_A);
    }

    @Test
    void tenantAReadingTenantBAlertReturnsNotFound() {
        when(alertEventMapper.selectByIdAndTenant(TENANT_A, ALERT_B))
                .thenReturn(null);

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.getAlert(tenantContext(), ALERT_B));

        assertEquals(404, exception.getHttpStatus());
        verify(alertEventMapper).selectByIdAndTenant(TENANT_A, ALERT_B);
    }

    @Test
    void platformContextCannotReadTenantAlertDirectly() {
        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.getAlert(platformContext(), ALERT_A));

        assertEquals(403, exception.getHttpStatus());
        verifyNoInteractions(alertEventMapper);
    }

    @Test
    void internalTokenAloneDoesNotGrantTenantAlertAccess() {
        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.getAlert(
                        TrustedRequestContext.internalService(
                                "request-internal"),
                        ALERT_A));

        assertEquals(403, exception.getHttpStatus());
        verifyNoInteractions(alertEventMapper);
    }

    private AlertEvent alert(Long id, Long tenantId) {
        AlertEvent alert = new AlertEvent();
        alert.setId(id);
        alert.setTenantId(tenantId);
        alert.setStatus(0);
        return alert;
    }

    private TrustedRequestContext tenantContext() {
        return TrustedRequestContext.user(
                TrustedSource.GATEWAY_USER,
                7L,
                1,
                "session-tenant-a",
                "token-tenant-a",
                TrustedContextType.TENANT,
                String.valueOf(TENANT_A),
                "tenant-a",
                "TENANT_ADMIN",
                1L,
                1L,
                Collections.emptyList(),
                "request-tenant-a");
    }

    private TrustedRequestContext platformContext() {
        return TrustedRequestContext.user(
                TrustedSource.GATEWAY_USER,
                1L,
                0,
                "session-platform",
                "token-platform",
                TrustedContextType.PLATFORM,
                null,
                null,
                null,
                null,
                null,
                Collections.singletonList("TENANT_MANAGE"),
                "request-platform");
    }
}
