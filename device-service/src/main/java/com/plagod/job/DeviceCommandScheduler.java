package com.plagod.job;

import com.plagod.constant.DeviceCommandStatus;
import com.plagod.constant.SessionStatus;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.mapper.DeviceCommandRecordMapper;
import com.plagod.service.DeviceCommandDispatchService;
import com.plagod.service.PortalSessionService;
import com.plagod.web.SafeExceptionLogFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "wifi.command", name = "scheduler-enabled", havingValue = "true", matchIfMissing = false)
public class DeviceCommandScheduler {

    @Autowired
    private DeviceCommandRecordMapper commandRecordMapper;

    @Autowired
    private DeviceCommandDispatchService commandDispatchService;

    @Autowired
    private PortalSessionService portalSessionService;

    @Value("${wifi.command.scan-batch-size:50}")
    private int scanBatchSize;

    @PostConstruct
    public void validateConfiguration() {
        if (scanBatchSize < 1 || scanBatchSize > 500) {
            throw new IllegalStateException("命令扫描批次必须在 1 到 500 之间");
        }
    }

    @Scheduled(fixedDelayString = "${wifi.command.dispatch-scan-interval-ms:1000}",
            initialDelayString = "${wifi.command.scheduler-initial-delay-ms:5000}")
    public void dispatchPendingCommands() {
        List<Long> commandIds = commandRecordMapper.selectDispatchableCommandIds(DeviceCommandStatus.PENDING, LocalDateTime.now(), scanBatchSize);
        for (Long commandId : commandIds) {
            try {
                // 每条命令通过独立 Bean 获取自己的事务和行锁。
                commandDispatchService.dispatchOne(commandId);
            } catch (Exception exception) {
                log.error(
                        "Outbox 命令发布处理失败，commandId={}, type={}, safeStack={}",
                        commandId,
                        exception.getClass().getName(),
                        SafeExceptionLogFormatter.format(exception));
            }
        }
    }


    @Scheduled(fixedDelayString = "${wifi.command.timeout-scan-interval-ms:1000}",
            initialDelayString = "${wifi.command.scheduler-initial-delay-ms:5000}")
    public void processTimedOutCommands() {
        List<Long> commandIds = commandRecordMapper.selectTimedOutCommandIds(DeviceCommandStatus.PUBLISHED, LocalDateTime.now(), scanBatchSize);

        for (Long commandId : commandIds) {
            try {
                commandDispatchService.timeoutOne(commandId);
            } catch (Exception exception) {
                log.error(
                        "命令结果超时处理失败，commandId={}, type={}, safeStack={}",
                        commandId,
                        exception.getClass().getName(),
                        SafeExceptionLogFormatter.format(exception));
            }
        }
    }

    @Scheduled(fixedDelayString = "${wifi.command.replacement-recovery-scan-interval-ms:1000}",
            initialDelayString = "${wifi.command.scheduler-initial-delay-ms:5000}")
    public void recoverWaitingReplacements() {
        List<DeviceCommandRecord> commands =
                commandRecordMapper.selectRecoverableForceReplacementCommands(
                        DeviceCommandStatus.SUCCEEDED,
                        SessionStatus.WAITING_REPLACEMENT,
                        scanBatchSize);
        for (DeviceCommandRecord command : commands) {
            try {
                portalSessionService.activateWaitingReplacement(
                        command.getTenantId(), command.getSessionId());
            } catch (Exception exception) {
                log.error(
                        "强制替换恢复处理失败，commandId={}, sessionId={}, type={}, safeStack={}",
                        command.getCommandId(),
                        command.getSessionId(),
                        exception.getClass().getName(),
                        SafeExceptionLogFormatter.format(exception));
            }
        }
    }

}
