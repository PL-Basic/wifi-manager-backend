package com.plagod.service;

import com.plagod.client.MonitorServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.DeviceTrafficEvent;
import com.plagod.dto.device.TrafficEvaluationRequest;
import com.plagod.entity.device.SessionRecord;
import com.plagod.entity.device.TrafficLog;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.vo.device.TrafficEvaluationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrafficTenantEvaluationTest {

    private static final Long TENANT_A = 11L;
    private static final Long TENANT_B = 22L;

    private MonitorServiceClient monitorServiceClient;
    private TrafficRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        monitorServiceClient = mock(MonitorServiceClient.class);
        evaluator = new TrafficRuleEvaluator();
        ReflectionTestUtils.setField(
                evaluator,
                "monitorServiceClient",
                monitorServiceClient);
        ReflectionTestUtils.setField(
                evaluator,
                "esp32NodeMapper",
                mock(Esp32NodeMapper.class));
        ReflectionTestUtils.setField(
                evaluator,
                "ruleActionExecutor",
                mock(RuleActionExecutor.class));

        TrafficEvaluationResult miss = new TrafficEvaluationResult();
        miss.setHit(false);
        when(monitorServiceClient.evaluate(any(
                TrafficEvaluationRequest.class)))
                .thenReturn(ApiResponse.success(miss));
    }

    @Test
    void propagatesEachPersistedTrafficSessionTenantWithoutCrossing() {
        evaluator.evaluateAndAct(
                event("event-a"),
                traffic("event-a", TENANT_A, 101L),
                session(TENANT_A, 101L));
        evaluator.evaluateAndAct(
                event("event-b"),
                traffic("event-b", TENANT_B, 202L),
                session(TENANT_B, 202L));

        ArgumentCaptor<TrafficEvaluationRequest> captor =
                ArgumentCaptor.forClass(TrafficEvaluationRequest.class);
        verify(monitorServiceClient,
                org.mockito.Mockito.times(2))
                .evaluate(captor.capture());

        List<TrafficEvaluationRequest> requests =
                captor.getAllValues();
        assertEquals(TENANT_A, requests.get(0).getTenantId());
        assertEquals(101L, requests.get(0).getSessionId());
        assertEquals(TENANT_B, requests.get(1).getTenantId());
        assertEquals(202L, requests.get(1).getSessionId());
    }

    @Test
    void rejectsCrossTenantPersistedRelationship() {
        evaluator.evaluateAndAct(
                event("event-cross"),
                traffic("event-cross", TENANT_A, 101L),
                session(TENANT_B, 101L));

        verify(monitorServiceClient, never())
                .evaluate(any(TrafficEvaluationRequest.class));
    }

    @Test
    void rejectsWrongPersistedSessionRelationship() {
        evaluator.evaluateAndAct(
                event("event-session"),
                traffic("event-session", TENANT_A, 101L),
                session(TENANT_A, 202L));

        verify(monitorServiceClient, never())
                .evaluate(any(TrafficEvaluationRequest.class));
    }

    private DeviceTrafficEvent event(String eventId) {
        DeviceTrafficEvent event = new DeviceTrafficEvent();
        event.setEventId(eventId);
        return event;
    }

    private TrafficLog traffic(String eventId,
                               Long tenantId,
                               Long sessionId) {
        TrafficLog trafficLog = new TrafficLog();
        trafficLog.setEventId(eventId);
        trafficLog.setTenantId(tenantId);
        trafficLog.setSessionId(sessionId);
        trafficLog.setDeviceCode("esp32-a");
        return trafficLog;
    }

    private SessionRecord session(Long tenantId, Long sessionId) {
        SessionRecord session = new SessionRecord();
        session.setTenantId(tenantId);
        session.setSessionId(sessionId);
        session.setUserId(7L);
        return session;
    }
}
