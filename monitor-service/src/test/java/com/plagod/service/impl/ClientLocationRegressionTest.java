package com.plagod.service.impl;

import com.plagod.client.DeviceLocationSessionClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.ClientLocationReportDTO;
import com.plagod.entity.monitor.ClientLocation;
import com.plagod.entity.monitor.LocationAuthorization;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.ClientLocationMapper;
import com.plagod.mapper.LocationAuthorizationMapper;
import com.plagod.security.MonitorTenantScope;
import com.plagod.security.TrustedContextType;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.TrustedSource;
import com.plagod.service.GeofenceEvaluationService;
import com.plagod.vo.device.LocationSessionContextVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientLocationRegressionTest {

    private static final Long USER_ID = 7L;
    private static final Long TENANT_A = 11L;
    private static final Long TENANT_B = 22L;
    private static final Long SESSION_ID = 55L;
    private static final Long NODE_ID = 9L;
    private static final String DEVICE_CODE = "esp32-main";
    private static final String MAC = "AA:BB:CC:DD:EE:FF";

    @Mock
    private ClientLocationMapper clientLocationMapper;

    @Mock
    private LocationAuthorizationMapper locationAuthorizationMapper;

    @Mock
    private DeviceLocationSessionClient deviceLocationSessionClient;

    @Mock
    private GeofenceEvaluationService geofenceEvaluationService;

    private ClientLocationServiceImpl clientLocationService;

    @BeforeEach
    void setUp() {
        clientLocationService = new ClientLocationServiceImpl();

        ReflectionTestUtils.setField(clientLocationService, "clientLocationMapper", clientLocationMapper);
        ReflectionTestUtils.setField(clientLocationService, "locationAuthorizationMapper", locationAuthorizationMapper);
        ReflectionTestUtils.setField(clientLocationService, "deviceLocationSessionClient", deviceLocationSessionClient);
        ReflectionTestUtils.setField(clientLocationService, "geofenceEvaluationService", geofenceEvaluationService);
        ReflectionTestUtils.setField(clientLocationService, "tenantScope", new MonitorTenantScope());
        ReflectionTestUtils.setField(clientLocationService, "minimumReportIntervalSeconds", 3L);
        ReflectionTestUtils.setField(clientLocationService, "maximumSpeedMetersPerSecond", 100.0D);
    }

    @Test
    void authorizedActiveSessionReportPersistsTrustedLocation() {
        ClientLocationReportDTO request = locationRequest();
        LocationSessionContextVO context = sessionContext();
        LocationAuthorization authorization = enabledAuthorization();

        when(deviceLocationSessionClient.getLocationContext(SESSION_ID))
                .thenReturn(ApiResponse.success(context));
        when(locationAuthorizationMapper.ensureAuthorizationRowByTenant(TENANT_A, USER_ID))
                .thenReturn(1);
        when(locationAuthorizationMapper.selectByUserIdForUpdateAndTenant(TENANT_A, USER_ID))
                .thenReturn(authorization);
        when(clientLocationMapper.selectLatestTrustedPointByTenant(
                TENANT_A,
                USER_ID,
                SESSION_ID)).thenReturn(null);

        when(clientLocationMapper.insert(any(ClientLocation.class)))
                .thenAnswer(invocation -> {
                    ClientLocation location = invocation.getArgument(0);
                    location.setId(301L);return 1;
                });

        when(locationAuthorizationMapper.updateByTenantAndVersion(
                eq(TENANT_A),
                eq(USER_ID),
                eq(1),
                eq(authorization.getConsentTime()),
                isNull(),
                any(LocalDateTime.class),
                eq(0))).thenReturn(1);

        Long locationId = clientLocationService.report(
                tenantContext(TENANT_A),
                SESSION_ID,
                request);

        assertEquals(301L, locationId);

        ArgumentCaptor<ClientLocation> locationCaptor = ArgumentCaptor.forClass(ClientLocation.class);

        verify(clientLocationMapper).insert(locationCaptor.capture());

        ClientLocation saved = locationCaptor.getValue();

        assertEquals(TENANT_A, saved.getTenantId());
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(SESSION_ID, saved.getSessionId());
        assertEquals(NODE_ID, saved.getNodeId());
        assertEquals(DEVICE_CODE, saved.getDeviceCode());
        assertEquals(MAC, saved.getMac());
        assertEquals(Integer.valueOf(1), saved.getTrustedBinding());
        assertEquals(request.getLatitude(), saved.getLatitude());
        assertEquals(request.getLongitude(), saved.getLongitude());
        assertEquals(request.getAccuracy(), saved.getAccuracy());
        assertEquals("portal", saved.getSource());
        assertEquals(authorization.getConsentTime(), saved.getConsentTime());
        assertNotNull(saved.getReportTime());
        assertNotNull(saved.getCreateTime());

        // 保存成功后必须更新上报时间并触发围栏计算。
        assertSame(saved.getReportTime(), authorization.getLastReportTime());
        verify(locationAuthorizationMapper).updateByTenantAndVersion(
                TENANT_A,
                USER_ID,
                1,
                authorization.getConsentTime(),
                null,
                authorization.getLastReportTime(),
                0);
        verify(geofenceEvaluationService).evaluate(isNull(), same(saved));
    }

    @Test
    void missingOrUnauthorizedSessionDoesNotWriteLocation() {
        when(deviceLocationSessionClient.getLocationContext(SESSION_ID))
                .thenReturn(ApiResponse.fail(
                        404,
                        "Session 不存在或无权访问"));

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> clientLocationService.report(
                        tenantContext(TENANT_A),
                        SESSION_ID,
                        locationRequest()));

        assertEquals(404, exception.getHttpStatus());
        assertEquals(404, exception.getCode());

        // Session 归属校验失败发生在任何数据库写入之前。
        verifyNoInteractions(clientLocationMapper, locationAuthorizationMapper, geofenceEvaluationService);
    }

    @Test
    void deviceTenantMismatchIsRejectedBeforeDatabaseWrite() {
        LocationSessionContextVO context = sessionContext();
        context.setTenantId(TENANT_B);
        when(deviceLocationSessionClient.getLocationContext(SESSION_ID))
                .thenReturn(ApiResponse.success(context));

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> clientLocationService.report(
                        tenantContext(TENANT_A),
                        SESSION_ID,
                        locationRequest()));

        assertEquals(502, exception.getHttpStatus());
        verifyNoInteractions(
                clientLocationMapper,
                locationAuthorizationMapper,
                geofenceEvaluationService);
    }

    @Test
    void ownedAndAdminQueriesKeepTenantABoundaries() {
        when(clientLocationMapper.selectPage(
                any(Page.class),
                any(QueryWrapper.class)))
                .thenReturn(new Page<>(1L, 10L));

        clientLocationService.pageOwnedLocations(
                tenantContext(TENANT_A),
                1L,
                10L,
                null,
                null,
                null);
        clientLocationService.pageLocations(
                tenantContext(TENANT_B),
                1L,
                10L,
                null,
                null,
                null,
                null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<ClientLocation>> queryCaptor =
                ArgumentCaptor.forClass(QueryWrapper.class);
        verify(clientLocationMapper, org.mockito.Mockito.times(2))
                .selectPage(any(Page.class), queryCaptor.capture());

        QueryWrapper<ClientLocation> tenantAQuery =
                queryCaptor.getAllValues().get(0);
        QueryWrapper<ClientLocation> tenantBQuery =
                queryCaptor.getAllValues().get(1);

        assertTrue(tenantAQuery.getCustomSqlSegment().contains("tenant_id"));
        assertTrue(tenantAQuery.getCustomSqlSegment().contains("user_id"));
        assertTrue(tenantAQuery.getParamNameValuePairs().containsValue(TENANT_A));
        assertTrue(tenantAQuery.getParamNameValuePairs().containsValue(USER_ID));

        assertTrue(tenantBQuery.getCustomSqlSegment().contains("tenant_id"));
        assertTrue(tenantBQuery.getParamNameValuePairs().containsValue(TENANT_B));
    }

    @Test
    void authorizationLookupUsesTrustedTenantAndUser() {
        LocationAuthorization authorization = enabledAuthorization();
        when(locationAuthorizationMapper.selectByUserIdAndTenant(
                TENANT_A,
                USER_ID)).thenReturn(authorization);

        clientLocationService.getAuthorization(tenantContext(TENANT_A));

        verify(locationAuthorizationMapper).selectByUserIdAndTenant(
                TENANT_A,
                USER_ID);
    }

    @Test
    void grantAuthorizationUsesTenantScopedOptimisticUpdate() {
        LocationAuthorization authorization = enabledAuthorization();
        authorization.setEnabled(0);
        authorization.setConsentTime(null);
        authorization.setVersion(3);
        when(locationAuthorizationMapper.ensureAuthorizationRowByTenant(
                TENANT_A,
                USER_ID)).thenReturn(1);
        when(locationAuthorizationMapper.selectByUserIdForUpdateAndTenant(
                TENANT_A,
                USER_ID)).thenReturn(authorization);
        when(locationAuthorizationMapper.updateByTenantAndVersion(
                eq(TENANT_A),
                eq(USER_ID),
                eq(1),
                any(LocalDateTime.class),
                isNull(),
                isNull(),
                eq(3))).thenReturn(1);

        clientLocationService.grantAuthorization(tenantContext(TENANT_A));

        verify(locationAuthorizationMapper).updateByTenantAndVersion(
                TENANT_A,
                USER_ID,
                1,
                authorization.getConsentTime(),
                null,
                null,
                3);
        assertEquals(Integer.valueOf(4), authorization.getVersion());
    }

    @Test
    void clearHistoryDeletesOnlyTrustedTenantAndUserData() {
        LocationAuthorization authorization = enabledAuthorization();
        when(locationAuthorizationMapper.ensureAuthorizationRowByTenant(
                TENANT_A,
                USER_ID)).thenReturn(1);
        when(locationAuthorizationMapper.selectByUserIdForUpdateAndTenant(
                TENANT_A,
                USER_ID)).thenReturn(authorization);
        when(clientLocationMapper.delete(any(QueryWrapper.class)))
                .thenReturn(2);

        long deleted = clientLocationService.clearOwnedHistory(
                tenantContext(TENANT_A));

        assertEquals(2L, deleted);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<ClientLocation>> queryCaptor =
                ArgumentCaptor.forClass(QueryWrapper.class);
        verify(clientLocationMapper).delete(queryCaptor.capture());
        QueryWrapper<ClientLocation> deleteQuery = queryCaptor.getValue();
        String sqlSegment = deleteQuery.getCustomSqlSegment();
        assertTrue(sqlSegment.contains("tenant_id"));
        assertTrue(sqlSegment.contains("user_id"));
        assertTrue(deleteQuery
                .getParamNameValuePairs().containsValue(TENANT_A));
        assertTrue(deleteQuery
                .getParamNameValuePairs().containsValue(USER_ID));
        verify(geofenceEvaluationService).clearUserData(TENANT_A, USER_ID);
    }

    private ClientLocationReportDTO locationRequest() {
        ClientLocationReportDTO request = new ClientLocationReportDTO();

        request.setLatitude(new BigDecimal("30.274084"));
        request.setLongitude(new BigDecimal("120.155070"));
        request.setAccuracy(new BigDecimal("8.5"));
        request.setSource("PORTAL");

        return request;
    }

    private LocationSessionContextVO sessionContext() {
        LocationSessionContextVO context = new LocationSessionContextVO();

        context.setSessionId(SESSION_ID);
        context.setTenantId(TENANT_A);
        context.setUserId(USER_ID);
        context.setNodeId(NODE_ID);
        context.setDeviceCode(DEVICE_CODE);
        context.setMac(MAC);

        return context;
    }

    private LocationAuthorization enabledAuthorization() {
        LocationAuthorization authorization = new LocationAuthorization();

        authorization.setTenantId(TENANT_A);
        authorization.setUserId(USER_ID);
        authorization.setEnabled(1);
        authorization.setVersion(0);
        authorization.setConsentTime(LocalDateTime.now().minusMinutes(10));
        authorization.setLastReportTime(null);

        return authorization;
    }

    private TrustedRequestContext tenantContext(Long tenantId) {
        return TrustedRequestContext.user(
                TrustedSource.GATEWAY_USER,
                USER_ID,
                1,
                "session-location",
                "token-location",
                TrustedContextType.TENANT,
                String.valueOf(tenantId),
                "tenant-" + tenantId,
                "TENANT_ADMIN",
                1L,
                1L,
                Collections.emptyList(),
                "request-location-" + tenantId);
    }
}
