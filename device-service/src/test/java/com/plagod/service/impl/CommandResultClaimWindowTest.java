package com.plagod.service.impl;

import com.plagod.constant.DeviceCommandStatus;
import com.plagod.dto.CommandResultEvent;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.entity.device.Esp32Node;
import com.plagod.mapper.DeviceCommandRecordMapper;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.service.DeviceWifiConfigLifecycleService;
import com.plagod.service.SessionCommandLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandResultClaimWindowTest {

    @Mock
    private DeviceCommandRecordMapper commandRecordMapper;
    @Mock
    private Esp32NodeMapper nodeMapper;
    @Mock
    private SessionCommandLifecycleService sessionCommandLifecycleService;
    @Mock
    private DeviceWifiConfigLifecycleService wifiConfigLifecycleService;

    private CommandResultEventServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommandResultEventServiceImpl();
        ReflectionTestUtils.setField(
                service, "commandRecordMapper", commandRecordMapper);
        ReflectionTestUtils.setField(service, "nodeMapper", nodeMapper);
        ReflectionTestUtils.setField(
                service,
                "sessionCommandLifecycleService",
                sessionCommandLifecycleService);
        ReflectionTestUtils.setField(
                service,
                "wifiConfigLifecycleService",
                wifiConfigLifecycleService);
    }

    @Test
    void resultCanFinalizeTheNarrowClaimedPendingWindow() {
        DeviceCommandRecord command = pendingCommand();
        command.setDispatchWorkerId("worker-a");
        command.setDispatchLeaseUntil(LocalDateTime.now().plusSeconds(30));
        stubCommand(command);
        when(commandRecordMapper.finalizeFromCommandResult(
                eq(10L),
                eq(1L),
                eq(DeviceCommandStatus.SUCCEEDED),
                eq(DeviceCommandStatus.PUBLISHED),
                eq(DeviceCommandStatus.PENDING),
                any(LocalDateTime.class),
                eq("applied"))).thenReturn(1);

        service.handleCommandResult(successEvent());

        verify(wifiConfigLifecycleService).handleTerminalCommand(command);
        verify(sessionCommandLifecycleService).handleTerminalCommand(command);
    }

    @Test
    void ordinaryPendingCommandStillRejectsResult() {
        DeviceCommandRecord command = pendingCommand();
        stubCommand(command);

        assertThrows(
                IllegalStateException.class,
                () -> service.handleCommandResult(successEvent()));

        verify(commandRecordMapper, never()).finalizeFromCommandResult(
                any(), any(), any(), any(), any(), any(), any());
        verify(wifiConfigLifecycleService, never())
                .handleTerminalCommand(any(DeviceCommandRecord.class));
        verify(sessionCommandLifecycleService, never())
                .handleTerminalCommand(any(DeviceCommandRecord.class));
    }

    private void stubCommand(DeviceCommandRecord command) {
        Esp32Node node = new Esp32Node();
        node.setTenantId(1L);
        node.setDeviceCode("node-1");
        when(nodeMapper.selectByDeviceCodeIncludeDeleted("node-1"))
                .thenReturn(node);
        when(commandRecordMapper.selectByRequestIdForUpdate(
                1L, "mqtt-request-fixed")).thenReturn(command);
    }

    private DeviceCommandRecord pendingCommand() {
        DeviceCommandRecord command = new DeviceCommandRecord();
        command.setCommandId(10L);
        command.setTenantId(1L);
        command.setDeviceCode("node-1");
        command.setRequestId("mqtt-request-fixed");
        command.setCommandType("ALLOW");
        command.setStatus(DeviceCommandStatus.PENDING);
        return command;
    }

    private CommandResultEvent successEvent() {
        CommandResultEvent event = new CommandResultEvent();
        event.setDeviceCode("node-1");
        event.setRequestId("mqtt-request-fixed");
        event.setType("ALLOW");
        event.setSuccess(true);
        event.setMessage("applied");
        return event;
    }
}
