package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.plagod.constant.DeviceCommandStatus;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.mapper.DeviceCommandRecordMapper;
import com.plagod.security.WifiCommandPayloadCrypto;
import com.plagod.service.DeviceWifiConfigLifecycleService;
import com.plagod.service.SessionCommandLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceCommandDispatchTransactionServiceTest {

    @Mock
    private DeviceCommandRecordMapper commandRecordMapper;
    @Mock
    private WifiCommandPayloadCrypto wifiCommandPayloadCrypto;
    @Mock
    private DeviceWifiConfigLifecycleService wifiConfigLifecycleService;
    @Mock
    private SessionCommandLifecycleService sessionCommandLifecycleService;

    private DeviceCommandDispatchTransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeviceCommandDispatchTransactionServiceImpl();
        ReflectionTestUtils.setField(
                service, "commandRecordMapper", commandRecordMapper);
        ReflectionTestUtils.setField(
                service, "wifiCommandPayloadCrypto", wifiCommandPayloadCrypto);
        ReflectionTestUtils.setField(
                service,
                "wifiConfigLifecycleService",
                wifiConfigLifecycleService);
        ReflectionTestUtils.setField(
                service,
                "sessionCommandLifecycleService",
                sessionCommandLifecycleService);
    }

    @Test
    void expiredLeaseCanBeReclaimedByAnotherWorker() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);
        DeviceCommandRecord command = pendingCommand();
        command.setRetryCount(1);
        command.setDispatchWorkerId("old-worker");
        command.setDispatchClaimedTime(now.minusMinutes(1));
        command.setDispatchLeaseUntil(now.minusSeconds(1));
        when(commandRecordMapper.selectByCommandIdForUpdate(10L))
                .thenReturn(command);
        when(commandRecordMapper.claimForDispatch(
                10L,
                "new-worker",
                now,
                now.plusSeconds(30),
                DeviceCommandStatus.PENDING,
                3)).thenReturn(1);

        DeviceCommandRecord claimed = service.claim(
                10L,
                "new-worker",
                now,
                now.plusSeconds(30),
                3,
                3L);

        assertSame(command, claimed);
        assertEquals("new-worker", claimed.getDispatchWorkerId());
        assertEquals(2, claimed.getRetryCount());
    }

    @Test
    void wrongWorkerCannotFinalizeClaim() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);
        DeviceCommandRecord command = pendingCommand();
        command.setRetryCount(1);
        command.setDispatchWorkerId("owner-worker");
        command.setDispatchLeaseUntil(now.plusSeconds(30));
        when(commandRecordMapper.selectByCommandIdForUpdate(10L))
                .thenReturn(command);

        boolean finalized = service.finalizeFailure(
                10L, "wrong-worker", now, 3, 3L);

        assertFalse(finalized);
        verify(commandRecordMapper, never()).finalizePublishFailure(
                any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(
                wifiConfigLifecycleService,
                sessionCommandLifecycleService);
    }

    @Test
    void expiredOwnerCannotFinalizeClaim() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);
        DeviceCommandRecord command = pendingCommand();
        command.setRetryCount(1);
        command.setDispatchWorkerId("owner-worker");
        command.setDispatchLeaseUntil(now);
        when(commandRecordMapper.selectByCommandIdForUpdate(10L))
                .thenReturn(command);

        assertFalse(service.finalizeFailure(
                10L, "owner-worker", now, 3, 3L));

        verify(commandRecordMapper, never()).finalizePublishFailure(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void exhaustedCrashWindowBecomesTerminalWithoutFourthPublish() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);
        DeviceCommandRecord command = pendingCommand();
        command.setRetryCount(3);
        command.setDispatchWorkerId("old-worker");
        command.setDispatchLeaseUntil(now.minusSeconds(1));
        when(commandRecordMapper.selectByCommandIdForUpdate(10L))
                .thenReturn(command);
        when(commandRecordMapper.update(
                eq(null), any(Wrapper.class))).thenReturn(1);

        DeviceCommandRecord claimed = service.claim(
                10L,
                "new-worker",
                now,
                now.plusSeconds(30),
                3,
                3L);

        assertNull(claimed);
        assertEquals(DeviceCommandStatus.PUBLISH_FAILED, command.getStatus());
        verify(commandRecordMapper, never()).claimForDispatch(
                any(), any(), any(), any(), any(), any());
        verify(wifiConfigLifecycleService).handleTerminalCommand(command);
        verify(sessionCommandLifecycleService).handleTerminalCommand(command);
    }

    private DeviceCommandRecord pendingCommand() {
        DeviceCommandRecord command = new DeviceCommandRecord();
        command.setCommandId(10L);
        command.setTenantId(1L);
        command.setStatus(DeviceCommandStatus.PENDING);
        command.setCommandType("ALLOW");
        command.setPurpose("PORTAL_AUTHORIZE");
        command.setNextRetryTime(null);
        return command;
    }
}
