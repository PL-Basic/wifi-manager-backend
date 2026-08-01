package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.client.UserEntitlementClient;
import com.plagod.client.UserPolicyClient;
import com.plagod.constant.SessionStatus;
import com.plagod.dto.DeviceTrafficEvent;
import com.plagod.dto.device.PortalAuthorizeDTO;
import com.plagod.entity.device.Esp32Node;
import com.plagod.entity.device.SessionRecord;
import com.plagod.entity.device.TrafficLog;
import com.plagod.mapper.*;
import com.plagod.service.ClientSignalQueryService;
import com.plagod.service.DeviceCommandService;
import com.plagod.service.SessionLeaseService;
import com.plagod.service.TrafficRuleEvaluator;
import com.plagod.vo.device.SessionRecordVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceIdempotencyRegressionTest {

    private static final String DEVICE_CODE = "esp32-main";
    private static final String MAC = "AA:BB:CC:DD:EE:FF";

    @Mock
    private Esp32NodeMapper esp32NodeMapper;
    @Mock
    private MacBlacklistMapper macBlacklistMapper;
    @Mock
    private SessionRecordMapper sessionRecordMapper;
    @Mock
    private DeviceCommandService deviceCommandService;
    @Mock
    private UserEntitlementClient userEntitlementClient;
    @Mock
    private ClientSignalQueryService clientSignalQueryService;
    @Mock
    private UserPolicyClient userPolicyClient;
    @Mock
    private SessionUserGuardMapper sessionUserGuardMapper;
    @Mock
    private SessionLeaseService sessionLeaseService;
    @Mock
    private ClientAccessGuardMapper clientAccessGuardMapper;
    @Mock
    private TrafficLogMapper trafficLogMapper;
    @Mock
    private TrafficRuleEvaluator trafficRuleEvaluator;

    private PortalSessionServiceImpl portalSessionService;
    private TrafficEventServiceImpl trafficEventService;

    @BeforeEach
    void setUp() {
        portalSessionService = new PortalSessionServiceImpl();

        ReflectionTestUtils.setField(portalSessionService, "esp32NodeMapper", esp32NodeMapper);
        ReflectionTestUtils.setField(portalSessionService, "macBlacklistMapper", macBlacklistMapper);
        ReflectionTestUtils.setField(portalSessionService, "sessionRecordMapper", sessionRecordMapper);
        ReflectionTestUtils.setField(portalSessionService, "deviceCommandService", deviceCommandService);
        ReflectionTestUtils.setField(portalSessionService, "userEntitlementClient", userEntitlementClient);
        ReflectionTestUtils.setField(portalSessionService, "clientSignalQueryService", clientSignalQueryService);
        ReflectionTestUtils.setField(portalSessionService, "userPolicyClient", userPolicyClient);
        ReflectionTestUtils.setField(portalSessionService, "sessionUserGuardMapper", sessionUserGuardMapper);
        ReflectionTestUtils.setField(portalSessionService, "sessionLeaseService", sessionLeaseService);
        ReflectionTestUtils.setField(portalSessionService, "clientAccessGuardMapper", clientAccessGuardMapper);
        ReflectionTestUtils.setField(portalSessionService, "internalToken", "test-internal-token");
        ReflectionTestUtils.setField(portalSessionService, "clientSignalMaxAgeSeconds", 30L);

        trafficEventService = new TrafficEventServiceImpl();

        ReflectionTestUtils.setField(trafficEventService, "trafficLogMapper", trafficLogMapper);
        ReflectionTestUtils.setField(trafficEventService, "sessionRecordMapper", sessionRecordMapper);
        ReflectionTestUtils.setField(trafficEventService, "esp32NodeMapper", esp32NodeMapper);
        ReflectionTestUtils.setField(trafficEventService, "trafficRuleEvaluator", trafficRuleEvaluator);
    }

    @Test
    void repeatedWaitingReplacementAuthorizationOnlyReturnsExistingSession() {
        PortalAuthorizeDTO request = new PortalAuthorizeDTO();
        request.setDeviceCode(DEVICE_CODE);
        request.setMac(MAC.toLowerCase());
        request.setIp("192.168.4.20");
        request.setDeviceInfo("test-browser");

        Esp32Node node = onlineNode();

        SessionRecord waiting = new SessionRecord();
        waiting.setSessionId(101L);
        waiting.setUserId(7L);
        waiting.setNodeId(node.getNodeId());
        waiting.setReplacedSessionId(88L);
        waiting.setMac(MAC);
        waiting.setIp("192.168.4.10");
        waiting.setStatus(SessionStatus.WAITING_REPLACEMENT);

        when(clientAccessGuardMapper.selectMacForUpdate(MAC)).thenReturn(MAC);
        when(esp32NodeMapper.selectByDeviceCodeForUpdateIncludeDeleted(DEVICE_CODE)).thenReturn(node);
        when(macBlacklistMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(clientSignalQueryService.wasRecentlyObserved(eq(node.getNodeId()), eq(DEVICE_CODE), eq(MAC), any(LocalDateTime.class))).thenReturn(true);
        when(sessionRecordMapper.selectOne(any(QueryWrapper.class))).thenReturn(waiting);

        SessionRecordVO result = portalSessionService.authorize(request, 7L);

        assertNotNull(result);
        assertEquals(101L, result.getSessionId());
        assertEquals(SessionStatus.WAITING_REPLACEMENT, result.getStatus());
        assertEquals(88L, result.getReplacedSessionId());

        verify(sessionRecordMapper, never()).insert(any(SessionRecord.class));
        verify(sessionRecordMapper, never()).updateById(any(SessionRecord.class));

        // WAITING_REPLACEMENT 从未发送 ALLOW，重复认证不能提前放行。
        verifyNoInteractions(userEntitlementClient, userPolicyClient, deviceCommandService, sessionLeaseService, sessionUserGuardMapper);
    }

    @Test
    void duplicateTrafficEventDoesNotAccumulateAgain() {
        DeviceTrafficEvent event = trafficEvent();
        Esp32Node node = onlineNode();
        SessionRecord session = activeSession(node);

        when(esp32NodeMapper.selectByDeviceCodeIncludeDeleted(DEVICE_CODE)).thenReturn(node);
        when(sessionRecordMapper.selectById(event.getSessionId())).thenReturn(session);
        when(trafficLogMapper.insertIgnore(any(TrafficLog.class))).thenReturn(0);
        when(trafficLogMapper.selectByEventIdentityForUpdate(DEVICE_CODE, event.getEventId())).thenReturn(existingTraffic(event, node, event.getBytesDown()));
        assertDoesNotThrow(() -> trafficEventService.handleTrafficEvent(event));

        verify(sessionRecordMapper, never()).incrementTrafficIfActive(anyLong(), anyLong(), anyString(), anyInt(), anyLong(), anyLong());

        verifyNoInteractions(trafficRuleEvaluator);
    }

    @Test
    void sameTrafficEventIdWithDifferentPayloadIsRejected() {
        DeviceTrafficEvent event = trafficEvent();
        Esp32Node node = onlineNode();
        SessionRecord session = activeSession(node);

        when(esp32NodeMapper.selectByDeviceCodeIncludeDeleted(DEVICE_CODE)).thenReturn(node);
        when(sessionRecordMapper.selectById(event.getSessionId())).thenReturn(session);
        when(trafficLogMapper.insertIgnore(any(TrafficLog.class))).thenReturn(0);

        // 已有记录使用相同 eventId，但字节数不同。
        when(trafficLogMapper.selectByEventIdentityForUpdate(DEVICE_CODE, event.getEventId())).thenReturn(existingTraffic(event, node, 9999L));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> trafficEventService.handleTrafficEvent(event));

        assertTrue(exception.getMessage().contains("eventId"));
        assertTrue(exception.getMessage().contains("不同流量事件"));

        verify(sessionRecordMapper, never()).incrementTrafficIfActive(anyLong(), anyLong(), anyString(), anyInt(), anyLong(), anyLong());

        verifyNoInteractions(trafficRuleEvaluator);
    }

    private Esp32Node onlineNode() {
        Esp32Node node = new Esp32Node();
        node.setNodeId(9L);
        node.setDeviceCode(DEVICE_CODE);
        node.setStatus(1);
        node.setDelFlag(0);
        return node;
    }

    private SessionRecord activeSession(Esp32Node node) {
        SessionRecord session = new SessionRecord();
        session.setSessionId(55L);
        session.setUserId(7L);
        session.setNodeId(node.getNodeId());
        session.setMac(MAC);
        session.setStatus(SessionStatus.ACTIVE);
        return session;
    }

    private DeviceTrafficEvent trafficEvent() {
        DeviceTrafficEvent event = new DeviceTrafficEvent();
        event.setEventId("stage212c-event-001");
        event.setDeviceCode(DEVICE_CODE);
        event.setSessionId(55L);
        event.setMac(MAC.toLowerCase());
        event.setDstIp("1.1.1.1");
        event.setDstPort(443);
        event.setSni("example.com");
        event.setProtocol("TCP");
        event.setBytesUp(123L);
        event.setBytesDown(456L);
        return event;
    }

    private TrafficLog existingTraffic(DeviceTrafficEvent event, Esp32Node node, Long bytesDown) {

        TrafficLog existing = new TrafficLog();
        existing.setEventId(event.getEventId());
        existing.setNodeId(node.getNodeId());
        existing.setDeviceCode(node.getDeviceCode());
        existing.setSessionId(event.getSessionId());
        existing.setMac(MAC);
        existing.setDstIp(event.getDstIp());
        existing.setDstPort(event.getDstPort());
        existing.setSni(event.getSni());
        existing.setProtocol(event.getProtocol());
        existing.setBytesUp(event.getBytesUp());
        existing.setBytesDown(bytesDown);
        existing.setLogTime(LocalDateTime.now().minusSeconds(1));
        return existing;
    }
}