package com.plagod.service.impl;

import com.plagod.client.DeviceLocationSessionClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.ClientLocationReportDTO;
import com.plagod.entity.monitor.ClientLocation;
import com.plagod.entity.monitor.LocationAuthorization;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.ClientLocationMapper;
import com.plagod.mapper.LocationAuthorizationMapper;
import com.plagod.service.GeofenceEvaluationService;
import com.plagod.vo.device.LocationSessionContextVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientLocationRegressionTest {

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 55L;
    private static final Long NODE_ID = 9L;
    private static final String DEVICE_CODE = "esp32-main";
    private static final String MAC = "AA:BB:CC:DD:EE:FF";
    private static final String INTERNAL_TOKEN = "test-internal-token";

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
        ReflectionTestUtils.setField(clientLocationService, "internalToken", INTERNAL_TOKEN);
        ReflectionTestUtils.setField(clientLocationService, "minimumReportIntervalSeconds", 3L);
        ReflectionTestUtils.setField(clientLocationService, "maximumSpeedMetersPerSecond", 100.0D);
    }

    @Test
    void authorizedActiveSessionReportPersistsTrustedLocation() {
        ClientLocationReportDTO request = locationRequest();
        LocationSessionContextVO context = sessionContext();
        LocationAuthorization authorization = enabledAuthorization();

        when(deviceLocationSessionClient.getLocationContext(SESSION_ID, USER_ID, INTERNAL_TOKEN)).thenReturn(ApiResponse.success(context));
        when(locationAuthorizationMapper.ensureAuthorizationRow(USER_ID)).thenReturn(1);
        when(locationAuthorizationMapper.selectByUserIdForUpdate(USER_ID)).thenReturn(authorization);
        when(clientLocationMapper.selectLatestTrustedPoint(USER_ID, SESSION_ID)).thenReturn(null);

        when(clientLocationMapper.insert(any(ClientLocation.class)))
                .thenAnswer(invocation -> {
                    ClientLocation location = invocation.getArgument(0);
                    location.setId(301L);return 1;
                });

        when(locationAuthorizationMapper.updateById(any(LocationAuthorization.class))).thenReturn(1);

        Long locationId = clientLocationService.report(SESSION_ID, request, USER_ID);

        assertEquals(301L, locationId);

        ArgumentCaptor<ClientLocation> locationCaptor = ArgumentCaptor.forClass(ClientLocation.class);

        verify(clientLocationMapper).insert(locationCaptor.capture());

        ClientLocation saved = locationCaptor.getValue();

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
        verify(locationAuthorizationMapper).updateById(same(authorization));
        verify(geofenceEvaluationService).evaluate(isNull(), same(saved));
    }

    @Test
    void missingOrUnauthorizedSessionDoesNotWriteLocation() {
        when(deviceLocationSessionClient.getLocationContext(SESSION_ID, USER_ID, INTERNAL_TOKEN)).thenReturn(ApiResponse.fail(404, "Session 不存在或无权访问"));

        ApiStatusException exception = assertThrows(ApiStatusException.class, () -> clientLocationService.report(SESSION_ID, locationRequest(), USER_ID));

        assertEquals(404, exception.getHttpStatus());
        assertEquals(404, exception.getCode());

        // Session 归属校验失败发生在任何数据库写入之前。
        verifyNoInteractions(clientLocationMapper, locationAuthorizationMapper, geofenceEvaluationService);
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
        context.setUserId(USER_ID);
        context.setNodeId(NODE_ID);
        context.setDeviceCode(DEVICE_CODE);
        context.setMac(MAC);

        return context;
    }

    private LocationAuthorization enabledAuthorization() {
        LocationAuthorization authorization = new LocationAuthorization();

        authorization.setUserId(USER_ID);
        authorization.setEnabled(1);
        authorization.setConsentTime(LocalDateTime.now().minusMinutes(10));
        authorization.setLastReportTime(null);

        return authorization;
    }
}