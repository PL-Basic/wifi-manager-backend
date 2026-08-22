package com.plagod.service.impl;

import com.plagod.dto.device.TrafficEvaluationRequest;
import com.plagod.entity.monitor.AccessRule;
import com.plagod.entity.monitor.AlertEvent;
import com.plagod.entity.monitor.RuleHitRecord;
import com.plagod.mapper.AccessRuleMapper;
import com.plagod.mapper.AlertEventMapper;
import com.plagod.mapper.RuleHitRecordMapper;
import com.plagod.observability.MonitorMetrics;
import com.plagod.service.AccessRuleCache;
import com.plagod.ws.AlertWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrafficEvaluationTenantIsolationTest {

    private static final Long TENANT_A = 11L;
    private static final Long TENANT_B = 22L;

    @Mock
    private AccessRuleCache accessRuleCache;
    @Mock
    private AlertEventMapper alertEventMapper;
    @Mock
    private RuleHitRecordMapper ruleHitRecordMapper;
    @Mock
    private AlertWebSocketHandler alertWebSocketHandler;
    @Mock
    private MonitorMetrics monitorMetrics;

    private TrafficEvaluationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TrafficEvaluationServiceImpl();
        ReflectionTestUtils.setField(
                service,
                "accessRuleCache",
                accessRuleCache);
        ReflectionTestUtils.setField(
                service,
                "alertEventMapper",
                alertEventMapper);
        ReflectionTestUtils.setField(
                service,
                "ruleHitRecordMapper",
                ruleHitRecordMapper);
        ReflectionTestUtils.setField(
                service,
                "alertWebSocketHandler",
                alertWebSocketHandler);
        ReflectionTestUtils.setField(
                service,
                "monitorMetrics",
                monitorMetrics);
        ReflectionTestUtils.setField(service, "cooldownSeconds", 30L);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void tenantABUseIndependentRulesCooldownAndPersistence() {
        AccessRule ruleA = matchingRule(101L, TENANT_A);
        AccessRule ruleB = matchingRule(202L, TENANT_B);
        when(accessRuleCache.getEnabledRules(TENANT_A))
                .thenReturn(java.util.Collections.singletonList(ruleA));
        when(accessRuleCache.getEnabledRules(TENANT_B))
                .thenReturn(java.util.Collections.singletonList(ruleB));
        when(ruleHitRecordMapper.insertIgnoreByTenant(
                any(RuleHitRecord.class))).thenReturn(1);

        AtomicLong alertIds = new AtomicLong(300L);
        when(alertEventMapper.insert(any(AlertEvent.class)))
                .thenAnswer(invocation -> {
                    AlertEvent alert = invocation.getArgument(0);
                    alert.setId(alertIds.incrementAndGet());
                    return 1;
                });
        when(ruleHitRecordMapper.bindAlertByTenant(
                any(Long.class),
                any(String.class),
                any(String.class),
                any(Long.class))).thenReturn(1);

        service.evaluate(request(TENANT_A));
        service.evaluate(request(TENANT_B));

        ArgumentCaptor<RuleHitRecord> hitCaptor =
                ArgumentCaptor.forClass(RuleHitRecord.class);
        verify(ruleHitRecordMapper, org.mockito.Mockito.times(2))
                .insertIgnoreByTenant(hitCaptor.capture());
        List<RuleHitRecord> hits = hitCaptor.getAllValues();
        assertEquals(TENANT_A, hits.get(0).getTenantId());
        assertEquals(TENANT_B, hits.get(1).getTenantId());
        assertEquals(Integer.valueOf(0), hits.get(0).getSuppressed());
        assertEquals(Integer.valueOf(0), hits.get(1).getSuppressed());

        ArgumentCaptor<AlertEvent> alertCaptor =
                ArgumentCaptor.forClass(AlertEvent.class);
        verify(alertEventMapper, org.mockito.Mockito.times(2))
                .insert(alertCaptor.capture());
        assertEquals(TENANT_A, alertCaptor.getAllValues().get(0).getTenantId());
        assertEquals(TENANT_B, alertCaptor.getAllValues().get(1).getTenantId());

        verify(ruleHitRecordMapper).bindAlertByTenant(
                TENANT_A,
                "device-main",
                "event-shared",
                301L);
        verify(ruleHitRecordMapper).bindAlertByTenant(
                TENANT_B,
                "device-main",
                "event-shared",
                302L);
    }

    @Test
    void missingOrInvalidTenantIsRejectedBeforeRuleLookup() {
        TrafficEvaluationRequest missingTenant = request(TENANT_A);
        missingTenant.setTenantId(null);
        TrafficEvaluationRequest invalidTenant = request(TENANT_A);
        invalidTenant.setTenantId(0L);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.evaluate(missingTenant));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.evaluate(invalidTenant));

        verifyNoInteractions(
                accessRuleCache,
                alertEventMapper,
                ruleHitRecordMapper,
                alertWebSocketHandler,
                monitorMetrics);
    }

    @Test
    void ruleCacheReturnsOnlyRequestedTenantRules() {
        AccessRuleMapper mapper =
                org.mockito.Mockito.mock(AccessRuleMapper.class);
        AccessRuleCache cache = new AccessRuleCache();
        ReflectionTestUtils.setField(cache, "accessRuleMapper", mapper);

        AccessRule ruleA = matchingRule(101L, TENANT_A);
        AccessRule ruleB = matchingRule(202L, TENANT_B);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(ruleA, ruleB));

        cache.reload();

        assertEquals(
                java.util.Collections.singletonList(ruleA),
                cache.getEnabledRules(TENANT_A));
        assertEquals(
                java.util.Collections.singletonList(ruleB),
                cache.getEnabledRules(TENANT_B));
        assertEquals(
                java.util.Collections.emptyList(),
                cache.getEnabledRules(33L));
        verify(mapper).selectList(any());
    }

    @Test
    void duplicateWithinOneTenantRemainsSuppressed() {
        AccessRule rule = matchingRule(101L, TENANT_A);
        when(accessRuleCache.getEnabledRules(TENANT_A))
                .thenReturn(java.util.Collections.singletonList(rule));
        when(ruleHitRecordMapper.insertIgnoreByTenant(
                any(RuleHitRecord.class))).thenReturn(1);
        when(alertEventMapper.insert(any(AlertEvent.class)))
                .thenAnswer(invocation -> {
                    AlertEvent alert = invocation.getArgument(0);
                    alert.setId(301L);
                    return 1;
                });
        when(ruleHitRecordMapper.bindAlertByTenant(
                TENANT_A,
                "device-main",
                "event-shared",
                301L)).thenReturn(1);

        service.evaluate(request(TENANT_A));
        service.evaluate(request(TENANT_A));

        ArgumentCaptor<RuleHitRecord> hitCaptor =
                ArgumentCaptor.forClass(RuleHitRecord.class);
        verify(ruleHitRecordMapper, org.mockito.Mockito.times(2))
                .insertIgnoreByTenant(hitCaptor.capture());
        assertEquals(
                Integer.valueOf(1),
                hitCaptor.getAllValues().get(1).getSuppressed());
        verify(alertEventMapper).insert(any(AlertEvent.class));
    }

    @Test
    void committedAlertBroadcastUsesPersistedTenantId() {
        AccessRule rule = matchingRule(101L, TENANT_A);
        when(accessRuleCache.getEnabledRules(TENANT_A))
                .thenReturn(java.util.Collections.singletonList(rule));
        when(ruleHitRecordMapper.insertIgnoreByTenant(
                any(RuleHitRecord.class))).thenReturn(1);
        when(alertEventMapper.insert(any(AlertEvent.class)))
                .thenAnswer(invocation -> {
                    AlertEvent alert = invocation.getArgument(0);
                    alert.setId(301L);
                    return 1;
                });
        when(ruleHitRecordMapper.bindAlertByTenant(
                TENANT_A,
                "device-main",
                "event-shared",
                301L)).thenReturn(1);

        service.evaluate(request(TENANT_A));
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager
                .getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(alertWebSocketHandler).broadcastToTenant(
                org.mockito.ArgumentMatchers.eq(TENANT_A),
                any());
    }

    private TrafficEvaluationRequest request(Long tenantId) {
        TrafficEvaluationRequest request = new TrafficEvaluationRequest();
        request.setTenantId(tenantId);
        request.setEventId("event-shared");
        request.setDeviceCode("device-main");
        request.setNodeId(9L);
        request.setSessionId(55L);
        request.setUserId(7L);
        request.setMac("AA:BB:CC:DD:EE:FF");
        request.setSni("blocked.example");
        request.setProtocol("TCP");
        request.setEventTime(LocalDateTime.now());
        return request;
    }

    private AccessRule matchingRule(Long id, Long tenantId) {
        AccessRule rule = new AccessRule();
        rule.setId(id);
        rule.setTenantId(tenantId);
        rule.setRuleCode("BLOCKED_DOMAIN");
        rule.setRuleType(1);
        rule.setPattern("blocked.example");
        rule.setActionType(1);
        rule.setLevel(1);
        rule.setEnabled(1);
        rule.setDescription("命中阻断域名");
        return rule;
    }
}
