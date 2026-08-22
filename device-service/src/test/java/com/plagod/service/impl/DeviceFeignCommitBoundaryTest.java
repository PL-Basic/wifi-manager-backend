package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.constant.DeviceCommandPurpose;
import com.plagod.constant.DeviceCommandStatus;
import com.plagod.constant.SessionStatus;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.ClientDisconnectEvent;
import com.plagod.dto.device.MacBlacklistCreateDTO;
import com.plagod.dto.device.PortalAuthorizeDTO;
import com.plagod.dto.user.EntitlementLeaseRequest;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.entity.device.Esp32Node;
import com.plagod.entity.device.SessionRecord;
import com.plagod.mapper.ClientAccessGuardMapper;
import com.plagod.mapper.ClientSignalMapper;
import com.plagod.mapper.DeviceCommandRecordMapper;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.mapper.MacBlacklistMapper;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.mapper.SessionUserGuardMapper;
import com.plagod.service.ClientSignalQueryService;
import com.plagod.service.DeviceCommandService;
import com.plagod.service.DeviceUserRemoteGateway;
import com.plagod.service.PortalSessionService;
import com.plagod.service.SessionLeaseService;
import com.plagod.vo.user.EntitlementLeaseResult;
import com.plagod.vo.user.UserConnectionPolicyVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceFeignCommitBoundaryTest {

    @Test
    void portalCommitsStableSessionBeforeInitialLeaseFeign() {
        PlatformTransactionManager transactionManager =
                transactionManager();
        TransactionStatus status = transactionStatus(transactionManager);
        DeviceUserRemoteGateway remoteGateway =
                mock(DeviceUserRemoteGateway.class);

        UserConnectionPolicyVO policy = new UserConnectionPolicyVO();
        policy.setUserId(7L);
        policy.setMaxConnections(2);
        when(remoteGateway.getConnectionPolicy(7L))
                .thenReturn(ApiResponse.success(policy));
        when(remoteGateway.acquireLease(any()))
                .thenReturn(ApiResponse.success(allowedLease()));

        PortalSessionServiceImpl service = new PortalSessionServiceImpl();
        Esp32NodeMapper nodeMapper = mock(Esp32NodeMapper.class);
        MacBlacklistMapper blacklistMapper = mock(MacBlacklistMapper.class);
        SessionRecordMapper sessionMapper = mock(SessionRecordMapper.class);
        DeviceCommandService commandService = mock(DeviceCommandService.class);
        ClientSignalQueryService signalService =
                mock(ClientSignalQueryService.class);
        SessionUserGuardMapper userGuardMapper =
                mock(SessionUserGuardMapper.class);
        SessionLeaseService leaseService = mock(SessionLeaseService.class);
        ClientAccessGuardMapper accessGuardMapper =
                mock(ClientAccessGuardMapper.class);
        DeviceCommandRecordMapper commandMapper =
                mock(DeviceCommandRecordMapper.class);

        ReflectionTestUtils.setField(service, "esp32NodeMapper", nodeMapper);
        ReflectionTestUtils.setField(
                service, "macBlacklistMapper", blacklistMapper);
        ReflectionTestUtils.setField(
                service, "sessionRecordMapper", sessionMapper);
        ReflectionTestUtils.setField(
                service, "deviceCommandService", commandService);
        ReflectionTestUtils.setField(
                service, "userRemoteGateway", remoteGateway);
        ReflectionTestUtils.setField(
                service, "clientSignalQueryService", signalService);
        ReflectionTestUtils.setField(
                service, "sessionUserGuardMapper", userGuardMapper);
        ReflectionTestUtils.setField(
                service, "sessionLeaseService", leaseService);
        ReflectionTestUtils.setField(
                service, "clientAccessGuardMapper", accessGuardMapper);
        ReflectionTestUtils.setField(
                service, "deviceCommandRecordMapper", commandMapper);
        ReflectionTestUtils.setField(
                service, "transactionManager", transactionManager);
        ReflectionTestUtils.setField(
                service, "clientSignalMaxAgeSeconds", 30L);

        Esp32Node node = onlineNode();
        when(accessGuardMapper.selectMacForUpdate(
                1L, "AA:BB:CC:DD:EE:FF"))
                .thenReturn("AA:BB:CC:DD:EE:FF");
        when(nodeMapper.selectByDeviceCodeForUpdateAndTenantIncludeDeleted(
                1L, "node-1")).thenReturn(node);
        when(blacklistMapper.selectCount(any(QueryWrapper.class)))
                .thenReturn(0L);
        when(signalService.wasRecentlyObserved(
                eq(1L),
                eq(9L),
                eq("node-1"),
                eq("AA:BB:CC:DD:EE:FF"),
                any(LocalDateTime.class))).thenReturn(true);
        when(userGuardMapper.selectUserIdForUpdate(1L, 7L))
                .thenReturn(7L);
        when(sessionMapper.countAllocatedSessionsExcludingMac(
                1L, 7L, "AA:BB:CC:DD:EE:FF")).thenReturn(0L);

        SessionRecord[] prepared = new SessionRecord[1];
        doAnswer(invocation -> {
            prepared[0] = invocation.getArgument(0);
            prepared[0].setSessionId(99L);
            return 1;
        }).when(sessionMapper).insert(any(SessionRecord.class));
        when(sessionMapper.selectByIdForUpdate(1L, 99L))
                .thenAnswer(invocation -> prepared[0]);
        when(sessionMapper.selectAllocatedByMacForUpdate(
                1L, "AA:BB:CC:DD:EE:FF"))
                .thenReturn(Collections.emptyList());
        when(sessionMapper.updateById(any(SessionRecord.class)))
                .thenReturn(1);
        when(sessionMapper.selectOwnedById(1L, 7L, 99L))
                .thenAnswer(invocation -> prepared[0]);

        PortalAuthorizeDTO request = portalRequest();
        service.authorize(1L, request, 7L);

        InOrder order = inOrder(transactionManager, remoteGateway);
        ArgumentCaptor<TransactionDefinition> transactionDefinition =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        order.verify(remoteGateway).getConnectionPolicy(7L);
        order.verify(transactionManager)
                .getTransaction(transactionDefinition.capture());
        order.verify(transactionManager).commit(status);
        order.verify(remoteGateway).acquireLease(any());
        assertEquals(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                transactionDefinition.getValue().getPropagationBehavior());

        ArgumentCaptor<EntitlementLeaseRequest> leaseRequest =
                ArgumentCaptor.forClass(EntitlementLeaseRequest.class);
        verify(remoteGateway).acquireLease(leaseRequest.capture());
        assertTrue(leaseRequest.getValue().getRequestId()
                .startsWith("portal-u7-"));
        assertTrue(leaseRequest.getValue().getRequestId().length() <= 64);
        assertEquals(99L, leaseRequest.getValue().getSessionId());
    }

    @Test
    void renewalCommitsLeasePlanBeforeFeign() {
        PlatformTransactionManager transactionManager =
                transactionManager();
        TransactionStatus status = transactionStatus(transactionManager);
        DeviceUserRemoteGateway remoteGateway =
                mock(DeviceUserRemoteGateway.class);
        when(remoteGateway.acquireLease(any()))
                .thenReturn(ApiResponse.success(allowedLease()));

        SessionRecordMapper sessionMapper = mock(SessionRecordMapper.class);
        Esp32NodeMapper nodeMapper = mock(Esp32NodeMapper.class);
        DeviceCommandService commandService = mock(DeviceCommandService.class);
        SessionLeaseServiceImpl service = new SessionLeaseServiceImpl();
        ReflectionTestUtils.setField(
                service, "sessionRecordMapper", sessionMapper);
        ReflectionTestUtils.setField(service, "esp32NodeMapper", nodeMapper);
        ReflectionTestUtils.setField(
                service, "userRemoteGateway", remoteGateway);
        ReflectionTestUtils.setField(
                service, "deviceCommandService", commandService);
        ReflectionTestUtils.setField(
                service, "transactionManager", transactionManager);
        ReflectionTestUtils.setField(service, "leaseTtlSeconds", 20);
        ReflectionTestUtils.setField(service, "offlineTimeoutSeconds", 30L);

        LocalDateTime now = LocalDateTime.now();
        SessionRecord session = activeSession(now);
        when(sessionMapper.selectByIdForUpdateGlobal(99L))
                .thenReturn(session);
        when(sessionMapper.selectByIdForUpdate(1L, 99L))
                .thenReturn(session);
        when(nodeMapper.selectByNodeIdAndTenantIncludeDeleted(1L, 9L))
                .thenReturn(onlineNode());
        when(sessionMapper.updateById(session)).thenReturn(1);

        service.processSession(99L);

        InOrder order = inOrder(transactionManager, remoteGateway);
        ArgumentCaptor<TransactionDefinition> transactionDefinition =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        order.verify(transactionManager)
                .getTransaction(transactionDefinition.capture());
        order.verify(transactionManager).commit(status);
        order.verify(remoteGateway).acquireLease(any());
        assertEquals(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                transactionDefinition.getValue().getPropagationBehavior());
    }

    @Test
    void renewalAndFinalSettlementReuseStableIdempotencyKey() {
        PlatformTransactionManager transactionManager =
                transactionManager();
        transactionStatus(transactionManager);
        DeviceUserRemoteGateway remoteGateway =
                mock(DeviceUserRemoteGateway.class);
        when(remoteGateway.acquireLease(any()))
                .thenReturn(ApiResponse.success(allowedLease()));

        SessionRecordMapper sessionMapper = mock(SessionRecordMapper.class);
        Esp32NodeMapper nodeMapper = mock(Esp32NodeMapper.class);
        DeviceCommandService commandService = mock(DeviceCommandService.class);
        SessionLeaseServiceImpl service = new SessionLeaseServiceImpl();
        ReflectionTestUtils.setField(
                service, "sessionRecordMapper", sessionMapper);
        ReflectionTestUtils.setField(service, "esp32NodeMapper", nodeMapper);
        ReflectionTestUtils.setField(
                service, "userRemoteGateway", remoteGateway);
        ReflectionTestUtils.setField(
                service, "deviceCommandService", commandService);
        ReflectionTestUtils.setField(
                service, "transactionManager", transactionManager);
        ReflectionTestUtils.setField(service, "leaseTtlSeconds", 20);
        ReflectionTestUtils.setField(service, "offlineTimeoutSeconds", 30L);

        LocalDateTime now = LocalDateTime.now();
        SessionRecord renewal = activeSession(now);
        SessionRecord finalSettlement = activeSession(now);
        when(sessionMapper.selectByIdForUpdateGlobal(99L))
                .thenReturn(renewal, finalSettlement);
        when(sessionMapper.selectByIdForUpdate(1L, 99L))
                .thenReturn(renewal, finalSettlement);
        when(nodeMapper.selectByNodeIdAndTenantIncludeDeleted(1L, 9L))
                .thenReturn(onlineNode());
        when(sessionMapper.updateById(any(SessionRecord.class)))
                .thenReturn(1);

        service.processSession(99L);
        service.settleFinalUsage(99L);

        ArgumentCaptor<EntitlementLeaseRequest> requests =
                ArgumentCaptor.forClass(EntitlementLeaseRequest.class);
        verify(remoteGateway, times(2)).acquireLease(requests.capture());
        assertEquals(
                requests.getAllValues().get(0).getRequestId(),
                requests.getAllValues().get(1).getRequestId());
        assertEquals(
                requests.getAllValues().get(0).getUsageSeconds(),
                requests.getAllValues().get(1).getUsageSeconds());
    }

    @Test
    void replacementActivationCommitsPlanBeforeFeign() {
        PlatformTransactionManager transactionManager =
                transactionManager();
        TransactionStatus status = transactionStatus(transactionManager);
        DeviceUserRemoteGateway remoteGateway =
                mock(DeviceUserRemoteGateway.class);
        when(remoteGateway.acquireLease(any()))
                .thenReturn(ApiResponse.success(allowedLease()));

        PortalSessionServiceImpl service = new PortalSessionServiceImpl();
        SessionRecordMapper sessionMapper = mock(SessionRecordMapper.class);
        Esp32NodeMapper nodeMapper = mock(Esp32NodeMapper.class);
        ClientAccessGuardMapper accessGuardMapper =
                mock(ClientAccessGuardMapper.class);
        DeviceCommandService commandService =
                mock(DeviceCommandService.class);
        ReflectionTestUtils.setField(
                service, "sessionRecordMapper", sessionMapper);
        ReflectionTestUtils.setField(
                service, "esp32NodeMapper", nodeMapper);
        ReflectionTestUtils.setField(
                service, "clientAccessGuardMapper", accessGuardMapper);
        ReflectionTestUtils.setField(
                service, "deviceCommandService", commandService);
        ReflectionTestUtils.setField(
                service, "userRemoteGateway", remoteGateway);
        ReflectionTestUtils.setField(
                service, "transactionManager", transactionManager);

        SessionRecord waiting = waitingReplacementSession();
        Esp32Node node = onlineNode();
        when(sessionMapper.selectWaitingReplacementForUpdate(1L, 88L))
                .thenReturn(waiting);
        when(nodeMapper.selectByNodeIdAndTenantIncludeDeleted(1L, 9L))
                .thenReturn(node);
        when(accessGuardMapper.selectMacForUpdate(
                1L, "AA:BB:CC:DD:EE:FF"))
                .thenReturn("AA:BB:CC:DD:EE:FF");
        when(sessionMapper.selectByIdForUpdate(1L, 99L))
                .thenReturn(waiting);
        when(nodeMapper.selectByDeviceCodeForUpdateAndTenantIncludeDeleted(
                1L, "node-1")).thenReturn(node);
        when(sessionMapper.updateById(waiting)).thenReturn(1);

        service.activateWaitingReplacement(1L, 88L);

        InOrder order = inOrder(transactionManager, remoteGateway);
        order.verify(transactionManager)
                .getTransaction(any(TransactionDefinition.class));
        order.verify(transactionManager).commit(status);
        order.verify(remoteGateway).acquireLease(any());
        order.verify(transactionManager)
                .getTransaction(any(TransactionDefinition.class));
    }

    @Test
    void forceReplacementSchedulesPortalActivationAfterCommit() {
        PortalSessionService portalSessionService =
                mock(PortalSessionService.class);
        SessionCommandLifecycleServiceImpl service =
                new SessionCommandLifecycleServiceImpl();
        ReflectionTestUtils.setField(
                service, "portalSessionService", portalSessionService);

        DeviceCommandRecord command = new DeviceCommandRecord();
        command.setTenantId(1L);
        command.setSessionId(88L);
        command.setCommandType("REVOKE_ACCESS");
        command.setPurpose(DeviceCommandPurpose.FORCE_LOGIN_REPLACE);
        command.setStatus(DeviceCommandStatus.SUCCEEDED);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.handleTerminalCommand(command);
            verify(portalSessionService, never())
                    .activateWaitingReplacement(1L, 88L);

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());
            synchronizations.get(0).afterCommit();

            verify(portalSessionService)
                    .activateWaitingReplacement(1L, 88L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deniedAuthorizationRetryUsesOriginalPreparedSession() {
        PlatformTransactionManager transactionManager =
                transactionManager();
        transactionStatus(transactionManager);
        DeviceUserRemoteGateway remoteGateway =
                mock(DeviceUserRemoteGateway.class);
        UserConnectionPolicyVO policy = new UserConnectionPolicyVO();
        policy.setUserId(7L);
        policy.setMaxConnections(2);
        when(remoteGateway.getConnectionPolicy(7L))
                .thenReturn(ApiResponse.success(policy));

        EntitlementLeaseResult denied = new EntitlementLeaseResult();
        denied.setAllowed(false);
        denied.setReason("NO_REMAINING_DURATION");
        when(remoteGateway.acquireLease(any()))
                .thenReturn(ApiResponse.success(denied));

        PortalSessionServiceImpl service = new PortalSessionServiceImpl();
        Esp32NodeMapper nodeMapper = mock(Esp32NodeMapper.class);
        MacBlacklistMapper blacklistMapper =
                mock(MacBlacklistMapper.class);
        SessionRecordMapper sessionMapper =
                mock(SessionRecordMapper.class);
        ClientSignalQueryService signalService =
                mock(ClientSignalQueryService.class);
        ClientAccessGuardMapper accessGuardMapper =
                mock(ClientAccessGuardMapper.class);
        DeviceCommandRecordMapper commandMapper =
                mock(DeviceCommandRecordMapper.class);
        ReflectionTestUtils.setField(
                service, "esp32NodeMapper", nodeMapper);
        ReflectionTestUtils.setField(
                service, "macBlacklistMapper", blacklistMapper);
        ReflectionTestUtils.setField(
                service, "sessionRecordMapper", sessionMapper);
        ReflectionTestUtils.setField(
                service, "userRemoteGateway", remoteGateway);
        ReflectionTestUtils.setField(
                service, "clientSignalQueryService", signalService);
        ReflectionTestUtils.setField(
                service, "clientAccessGuardMapper", accessGuardMapper);
        ReflectionTestUtils.setField(
                service, "deviceCommandRecordMapper", commandMapper);
        ReflectionTestUtils.setField(
                service, "transactionManager", transactionManager);
        ReflectionTestUtils.setField(
                service, "clientSignalMaxAgeSeconds", 30L);

        PortalAuthorizeDTO request = portalRequest();
        SessionRecord deniedSession = new SessionRecord();
        deniedSession.setSessionId(99L);
        deniedSession.setTenantId(1L);
        deniedSession.setUserId(7L);
        deniedSession.setNodeId(9L);
        deniedSession.setMac("AA:BB:CC:DD:EE:FF");
        deniedSession.setStatus(SessionStatus.CLOSED);
        deniedSession.setClientRequestId(request.getClientRequestId());
        deniedSession.setRequestFingerprint(
                com.plagod.service.PortalAuthorizationFingerprint.calculate(
                        1L, 7L, request));

        when(accessGuardMapper.selectMacForUpdate(
                1L, "AA:BB:CC:DD:EE:FF"))
                .thenReturn("AA:BB:CC:DD:EE:FF");
        when(nodeMapper.selectByDeviceCodeForUpdateAndTenantIncludeDeleted(
                1L, "node-1")).thenReturn(onlineNode());
        when(blacklistMapper.selectCount(any(QueryWrapper.class)))
                .thenReturn(0L);
        when(signalService.wasRecentlyObserved(
                eq(1L),
                eq(9L),
                eq("node-1"),
                eq("AA:BB:CC:DD:EE:FF"),
                any(LocalDateTime.class))).thenReturn(true);
        when(sessionMapper.selectByAuthorizeRequestForUpdate(
                1L, 7L, request.getClientRequestId()))
                .thenReturn(deniedSession);
        when(sessionMapper.selectByIdForUpdate(1L, 99L))
                .thenReturn(deniedSession);
        when(sessionMapper.updateById(deniedSession)).thenReturn(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.authorize(1L, request, 7L));

        ArgumentCaptor<EntitlementLeaseRequest> leaseRequest =
                ArgumentCaptor.forClass(EntitlementLeaseRequest.class);
        verify(remoteGateway).acquireLease(leaseRequest.capture());
        assertEquals(99L, leaseRequest.getValue().getSessionId());
        assertTrue(leaseRequest.getValue().getRequestId()
                .startsWith("portal-u7-"));
        verify(sessionMapper, never()).insert(any(SessionRecord.class));
    }

    @Test
    void portalEntitlementKeySeparatesUsersAndStaysBounded() {
        DeviceUserRemoteGateway remoteGateway =
                mock(DeviceUserRemoteGateway.class);
        when(remoteGateway.acquireLease(any()))
                .thenReturn(ApiResponse.success(allowedLease()));
        PortalSessionServiceImpl service = new PortalSessionServiceImpl();
        ReflectionTestUtils.setField(
                service, "userRemoteGateway", remoteGateway);

        ReflectionTestUtils.invokeMethod(
                service,
                "acquireInitialLease",
                1L,
                7L,
                99L,
                "same-client-request");
        ReflectionTestUtils.invokeMethod(
                service,
                "acquireInitialLease",
                1L,
                8L,
                100L,
                "same-client-request");

        ArgumentCaptor<EntitlementLeaseRequest> requests =
                ArgumentCaptor.forClass(EntitlementLeaseRequest.class);
        verify(remoteGateway, times(2)).acquireLease(requests.capture());
        String first = requests.getAllValues().get(0).getRequestId();
        String second = requests.getAllValues().get(1).getRequestId();
        assertTrue(first.startsWith("portal-u7-"));
        assertTrue(second.startsWith("portal-u8-"));
        assertNotEquals(first, second);
        assertTrue(first.length() <= 64);
        assertTrue(second.length() <= 64);
    }

    @Test
    void finalSettlementEntryPointsSuspendCallerTransaction()
            throws Exception {
        assertNotSupported(
                SessionRevokeServiceImpl.class,
                "logout",
                Long.class,
                Long.class,
                Long.class);
        assertNotSupported(
                SessionRevokeServiceImpl.class,
                "adminRevoke",
                Long.class,
                Long.class,
                Integer.class);
        assertNotSupported(
                ClientDisconnectEventServiceImpl.class,
                "handleClientDisconnectEvent",
                ClientDisconnectEvent.class);
        assertNotSupported(
                MacBlacklistServiceImpl.class,
                "addBlacklist",
                Long.class,
                MacBlacklistCreateDTO.class);
    }

    @Test
    void logoutReleasesPreparationLockBeforeFinalSettlement() {
        PlatformTransactionManager transactionManager =
                transactionManager();
        TransactionStatus preparation = mock(TransactionStatus.class);
        TransactionStatus completion = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(
                any(TransactionDefinition.class)))
                .thenReturn(preparation, completion);

        SessionRecordMapper sessionMapper = mock(SessionRecordMapper.class);
        Esp32NodeMapper nodeMapper = mock(Esp32NodeMapper.class);
        SessionLeaseService leaseService = mock(SessionLeaseService.class);
        DeviceCommandService commandService = mock(DeviceCommandService.class);
        SessionRevokeServiceImpl service = new SessionRevokeServiceImpl();
        ReflectionTestUtils.setField(
                service, "sessionRecordMapper", sessionMapper);
        ReflectionTestUtils.setField(
                service, "esp32NodeMapper", nodeMapper);
        ReflectionTestUtils.setField(
                service, "sessionLeaseService", leaseService);
        ReflectionTestUtils.setField(
                service, "deviceCommandService", commandService);
        ReflectionTestUtils.setField(
                service, "transactionManager", transactionManager);

        SessionRecord session = activeSession(LocalDateTime.now());
        when(sessionMapper.selectByIdForUpdate(1L, 99L))
                .thenReturn(session);
        when(sessionMapper.updateById(session)).thenReturn(1);
        when(nodeMapper.selectByNodeIdAndTenantIncludeDeleted(1L, 9L))
                .thenReturn(onlineNode());

        service.logout(1L, 99L, 7L);

        InOrder order = inOrder(transactionManager, leaseService);
        order.verify(transactionManager)
                .getTransaction(any(TransactionDefinition.class));
        order.verify(transactionManager).commit(preparation);
        order.verify(leaseService).settleFinalUsage(99L);
        order.verify(transactionManager)
                .getTransaction(any(TransactionDefinition.class));
        order.verify(transactionManager).commit(completion);
    }

    private void assertNotSupported(
            Class<?> type,
            String methodName,
            Class<?>... parameterTypes) throws Exception {
        Transactional transactional = type
                .getMethod(methodName, parameterTypes)
                .getAnnotation(Transactional.class);
        assertEquals(Propagation.NOT_SUPPORTED, transactional.propagation());
    }

    private PlatformTransactionManager transactionManager() {
        return mock(PlatformTransactionManager.class);
    }

    private TransactionStatus transactionStatus(
            PlatformTransactionManager transactionManager) {
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(
                any(TransactionDefinition.class))).thenReturn(status);
        return status;
    }

    private PortalAuthorizeDTO portalRequest() {
        PortalAuthorizeDTO request = new PortalAuthorizeDTO();
        request.setClientRequestId("portal-request-commit");
        request.setDeviceCode("node-1");
        request.setMac("AA:BB:CC:DD:EE:FF");
        request.setIp("192.168.4.2");
        request.setDeviceInfo("browser");
        request.setForceReplaceOldest(false);
        return request;
    }

    private EntitlementLeaseResult allowedLease() {
        EntitlementLeaseResult lease = new EntitlementLeaseResult();
        lease.setAllowed(true);
        lease.setEntitlementId(5L);
        lease.setMode("DURATION");
        lease.setTtlSeconds(20);
        lease.setChargedSeconds(0L);
        return lease;
    }

    private Esp32Node onlineNode() {
        Esp32Node node = new Esp32Node();
        node.setNodeId(9L);
        node.setTenantId(1L);
        node.setDeviceCode("node-1");
        node.setStatus(1);
        node.setDelFlag(0);
        return node;
    }

    private SessionRecord activeSession(LocalDateTime now) {
        SessionRecord session = new SessionRecord();
        session.setSessionId(99L);
        session.setTenantId(1L);
        session.setUserId(7L);
        session.setEntitlementId(5L);
        session.setNodeId(9L);
        session.setMac("AA:BB:CC:DD:EE:FF");
        session.setStatus(1);
        session.setLoginTime(now.minusMinutes(1));
        session.setLastBilledTime(now.minusSeconds(5));
        session.setLastSeenTime(now.minusSeconds(1));
        session.setConsumedSeconds(0L);
        return session;
    }

    private SessionRecord waitingReplacementSession() {
        SessionRecord session = new SessionRecord();
        session.setSessionId(99L);
        session.setTenantId(1L);
        session.setUserId(7L);
        session.setNodeId(9L);
        session.setReplacedSessionId(88L);
        session.setMac("AA:BB:CC:DD:EE:FF");
        session.setStatus(SessionStatus.WAITING_REPLACEMENT);
        session.setClientRequestId("portal-request-commit");
        session.setRequestFingerprint(repeat('a', 64));
        return session;
    }

    private String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
