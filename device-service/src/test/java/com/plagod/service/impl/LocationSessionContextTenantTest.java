package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.constant.SessionStatus;
import com.plagod.entity.device.Esp32Node;
import com.plagod.entity.device.SessionRecord;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.vo.device.LocationSessionContextVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class LocationSessionContextTenantTest {

    private static final Long TENANT_A = 11L;
    private static final Long TENANT_B = 22L;
    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 101L;
    private static final Long NODE_ID = 301L;

    private SessionRecordMapper sessionRecordMapper;
    private Esp32NodeMapper esp32NodeMapper;
    private SessionQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        sessionRecordMapper = mock(SessionRecordMapper.class);
        esp32NodeMapper = mock(Esp32NodeMapper.class);
        service = new SessionQueryServiceImpl();
        ReflectionTestUtils.setField(
                service,
                "sessionRecordMapper",
                sessionRecordMapper);
        ReflectionTestUtils.setField(
                service,
                "esp32NodeMapper",
                esp32NodeMapper);
        ReflectionTestUtils.setField(
                service,
                "sessionOfflineTimeoutSeconds",
                30L);
        ReflectionTestUtils.setField(
                service,
                "heartbeatTimeoutSeconds",
                60L);
    }

    @Test
    void returnsTenantFromTenantScopedPersistedSessionRelationship() {
        SessionRecord session = activeSession(TENANT_A);
        Esp32Node node = onlineNode(TENANT_A);
        when(sessionRecordMapper.selectOne(any(Wrapper.class)))
                .thenReturn(session);
        when(esp32NodeMapper.selectByNodeIdAndTenantIncludeDeleted(
                TENANT_A,
                NODE_ID)).thenReturn(node);

        LocationSessionContextVO result =
                service.getLocationContext(
                        TENANT_A,
                        USER_ID,
                        SESSION_ID);

        assertEquals(TENANT_A, result.getTenantId());
        assertEquals(SESSION_ID, result.getSessionId());
        assertEquals(NODE_ID, result.getNodeId());
        verify(esp32NodeMapper)
                .selectByNodeIdAndTenantIncludeDeleted(
                        TENANT_A,
                        NODE_ID);

        ArgumentCaptor<Wrapper> captor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(sessionRecordMapper).selectOne(captor.capture());
        Map<String, Object> parameters =
                boundParameters(captor.getValue());
        assertTrue(parameters.containsValue(TENANT_A), parameters.toString());
        assertTrue(parameters.containsValue(SESSION_ID), parameters.toString());
    }

    @Test
    void wrongTenantCannotEnumerateLocationSession() {
        when(sessionRecordMapper.selectOne(any(Wrapper.class)))
                .thenReturn(null);

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.getLocationContext(
                        TENANT_B,
                        USER_ID,
                        SESSION_ID));

        assertEquals(404, exception.getHttpStatus());
        verify(esp32NodeMapper, never())
                .selectByNodeIdAndTenantIncludeDeleted(
                        any(Long.class),
                        any(Long.class));

        ArgumentCaptor<Wrapper> captor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(sessionRecordMapper).selectOne(captor.capture());
        assertTrue(
                boundParameters(captor.getValue())
                        .containsValue(TENANT_B));
    }

    @Test
    void rejectsUnexpectedTenantReturnedByPersistenceBoundary() {
        when(sessionRecordMapper.selectOne(any(Wrapper.class)))
                .thenReturn(activeSession(TENANT_A));

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.getLocationContext(
                        TENANT_B,
                        USER_ID,
                        SESSION_ID));

        assertEquals(404, exception.getHttpStatus());
        verify(esp32NodeMapper, never())
                .selectByNodeIdAndTenantIncludeDeleted(
                        any(Long.class),
                        any(Long.class));
    }

    private SessionRecord activeSession(Long tenantId) {
        LocalDateTime now = LocalDateTime.now();
        SessionRecord session = new SessionRecord();
        session.setSessionId(SESSION_ID);
        session.setTenantId(tenantId);
        session.setUserId(USER_ID);
        session.setNodeId(NODE_ID);
        session.setMac("AA:BB:CC:DD:EE:FF");
        session.setStatus(SessionStatus.ACTIVE);
        session.setExpireTime(now.plusMinutes(5));
        session.setLastSeenTime(now.minusSeconds(2));
        return session;
    }

    private Esp32Node onlineNode(Long tenantId) {
        Esp32Node node = new Esp32Node();
        node.setNodeId(NODE_ID);
        node.setTenantId(tenantId);
        node.setDeviceCode("esp32-a");
        node.setStatus(1);
        node.setLastHeartbeat(LocalDateTime.now().minusSeconds(2));
        return node;
    }

    private Map<String, Object> boundParameters(Wrapper<?> wrapper) {
        QueryWrapper<?> queryWrapper = (QueryWrapper<?>) wrapper;
        queryWrapper.getSqlSegment();
        return queryWrapper.getParamNameValuePairs();
    }
}
