package com.plagod.job;

import com.plagod.constant.DeviceCommandStatus;
import com.plagod.constant.SessionStatus;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.mapper.DeviceCommandRecordMapper;
import com.plagod.service.PortalSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForceReplacementRecoveryTest {

    @Test
    void committedReplacementIsRetriedAfterActivationCrash() {
        DeviceCommandRecordMapper commandMapper =
                mock(DeviceCommandRecordMapper.class);
        PortalSessionService portalSessionService =
                mock(PortalSessionService.class);
        DeviceCommandScheduler scheduler = new DeviceCommandScheduler();
        ReflectionTestUtils.setField(
                scheduler, "commandRecordMapper", commandMapper);
        ReflectionTestUtils.setField(
                scheduler, "portalSessionService", portalSessionService);
        ReflectionTestUtils.setField(scheduler, "scanBatchSize", 50);

        DeviceCommandRecord command = new DeviceCommandRecord();
        command.setCommandId(101L);
        command.setTenantId(11L);
        command.setSessionId(88L);
        when(commandMapper.selectRecoverableForceReplacementCommands(
                DeviceCommandStatus.SUCCEEDED,
                SessionStatus.WAITING_REPLACEMENT,
                50)).thenReturn(Collections.singletonList(command));
        doThrow(new IllegalStateException("simulated crash"))
                .doNothing()
                .when(portalSessionService)
                .activateWaitingReplacement(11L, 88L);

        scheduler.recoverWaitingReplacements();
        scheduler.recoverWaitingReplacements();

        verify(commandMapper, times(2))
                .selectRecoverableForceReplacementCommands(
                        DeviceCommandStatus.SUCCEEDED,
                        SessionStatus.WAITING_REPLACEMENT,
                        50);
        verify(portalSessionService, times(2))
                .activateWaitingReplacement(11L, 88L);
    }
}
