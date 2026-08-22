package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.plagod.constant.DeviceCommandPurpose;
import com.plagod.constant.DeviceCommandStatus;
import com.plagod.constant.DeviceCommandType;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.mapper.DeviceCommandRecordMapper;
import com.plagod.security.WifiCommandPayloadCrypto;
import com.plagod.service.DeviceCommandDispatchTransactionService;
import com.plagod.service.DeviceWifiConfigLifecycleService;
import com.plagod.service.SessionCommandLifecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class DeviceCommandDispatchTransactionServiceImpl
        implements DeviceCommandDispatchTransactionService {

    @Autowired
    private DeviceCommandRecordMapper commandRecordMapper;

    @Autowired
    private WifiCommandPayloadCrypto wifiCommandPayloadCrypto;

    @Autowired
    private DeviceWifiConfigLifecycleService wifiConfigLifecycleService;

    @Autowired
    private SessionCommandLifecycleService sessionCommandLifecycleService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public DeviceCommandRecord claim(
            Long commandId,
            String workerId,
            LocalDateTime now,
            LocalDateTime leaseUntil,
            int publishMaxAttempts,
            long retryDelaySeconds) {
        DeviceCommandRecord command =
                commandRecordMapper.selectByCommandIdForUpdate(commandId);
        if (!isClaimable(command, now)) {
            return null;
        }

        if (isSensitiveCommandUnavailable(command)) {
            deferSensitiveCommand(command, now, retryDelaySeconds);
            return null;
        }
        if (shouldWaitForEarlierSessionAllow(command)) {
            return null;
        }

        int attempts = command.getRetryCount() == null
                ? 0
                : command.getRetryCount();
        if (attempts >= publishMaxAttempts) {
            markPublishFailed(command, now);
            return null;
        }

        int claimed = commandRecordMapper.claimForDispatch(
                commandId,
                workerId,
                now,
                leaseUntil,
                DeviceCommandStatus.PENDING,
                publishMaxAttempts);
        if (claimed != 1) {
            return null;
        }

        command.setDispatchWorkerId(workerId);
        command.setDispatchClaimedTime(now);
        command.setDispatchLeaseUntil(leaseUntil);
        command.setRetryCount(attempts + 1);
        command.setUpdateTime(now);
        return command;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean finalizePublished(
            Long commandId,
            String workerId,
            LocalDateTime now,
            LocalDateTime deadlineTime) {
        return commandRecordMapper.finalizePublished(
                commandId,
                workerId,
                now,
                deadlineTime,
                DeviceCommandStatus.PENDING,
                DeviceCommandStatus.PUBLISHED) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean finalizeFailure(
            Long commandId,
            String workerId,
            LocalDateTime now,
            int publishMaxAttempts,
            long retryDelaySeconds) {
        DeviceCommandRecord command =
                commandRecordMapper.selectByCommandIdForUpdate(commandId);
        if (!isOwnedBy(command, workerId, now)) {
            return false;
        }

        int attempts = command.getRetryCount() == null
                ? 0
                : command.getRetryCount();
        boolean terminal = attempts >= publishMaxAttempts;
        Integer nextStatus = terminal
                ? DeviceCommandStatus.PUBLISH_FAILED
                : DeviceCommandStatus.PENDING;
        LocalDateTime nextRetryTime = terminal
                ? null
                : now.plusSeconds(retryDelaySeconds);

        int finalized = commandRecordMapper.finalizePublishFailure(
                commandId,
                workerId,
                now,
                nextStatus,
                nextRetryTime,
                terminal ? now : null,
                "MQTT 发布失败",
                DeviceCommandStatus.PENDING);
        if (finalized != 1) {
            return false;
        }

        if (terminal) {
            command.setStatus(DeviceCommandStatus.PUBLISH_FAILED);
            command.setResultTime(now);
            command.setResultMessage("MQTT 发布失败");
            clearEncryptedPayload(command, now);
            wifiConfigLifecycleService.handleTerminalCommand(command);
            sessionCommandLifecycleService.handleTerminalCommand(command);
        }
        return true;
    }

    private boolean isClaimable(
            DeviceCommandRecord command,
            LocalDateTime now) {
        if (command == null
                || !Integer.valueOf(DeviceCommandStatus.PENDING)
                .equals(command.getStatus())) {
            return false;
        }
        if (command.getNextRetryTime() != null
                && command.getNextRetryTime().isAfter(now)) {
            return false;
        }
        return command.getDispatchLeaseUntil() == null
                || !command.getDispatchLeaseUntil().isAfter(now);
    }

    private boolean isOwnedBy(
            DeviceCommandRecord command,
            String workerId,
            LocalDateTime now) {
        return command != null
                && Integer.valueOf(DeviceCommandStatus.PENDING)
                .equals(command.getStatus())
                && workerId != null
                && workerId.equals(command.getDispatchWorkerId())
                && command.getDispatchLeaseUntil() != null
                && command.getDispatchLeaseUntil().isAfter(now);
    }

    private boolean isSensitiveCommandUnavailable(
            DeviceCommandRecord command) {
        return DeviceCommandType.isSensitiveType(command.getCommandType())
                && DeviceCommandPurpose.isSensitivePurpose(command.getPurpose())
                && !wifiCommandPayloadCrypto.isAvailable();
    }

    private boolean shouldWaitForEarlierSessionAllow(
            DeviceCommandRecord command) {
        if (!DeviceCommandType.REVOKE_ACCESS.equals(command.getCommandType())
                || !DeviceCommandPurpose.isSessionRevokePurpose(
                command.getPurpose())
                || command.getSessionId() == null
                || command.getSessionId() <= 0) {
            return false;
        }
        return commandRecordMapper.countEarlierPendingSessionAllowCommands(
                command.getTenantId(),
                command.getSessionId(),
                command.getCommandId(),
                DeviceCommandStatus.PENDING) > 0;
    }

    private void deferSensitiveCommand(
            DeviceCommandRecord command,
            LocalDateTime now,
            long retryDelaySeconds) {
        UpdateWrapper<DeviceCommandRecord> update = ownedCommand(command)
                .set("next_retry_time", now.plusSeconds(retryDelaySeconds))
                .set("result_message", "敏感命令功能暂不可用，等待密钥配置")
                .set("update_time", now);
        requireSingleUpdate(update, "敏感命令延后失败");
    }

    private void markPublishFailed(
            DeviceCommandRecord command,
            LocalDateTime now) {
        UpdateWrapper<DeviceCommandRecord> update = ownedCommand(command)
                .set("status", DeviceCommandStatus.PUBLISH_FAILED)
                .set("next_retry_time", null)
                .set("dispatch_worker_id", null)
                .set("dispatch_lease_until", null)
                .set("dispatch_claimed_time", null)
                .set("result_time", now)
                .set("result_message", "MQTT 发布尝试次数已耗尽")
                .set("update_time", now);
        requireSingleUpdate(update, "发布耗尽状态保存失败");

        command.setStatus(DeviceCommandStatus.PUBLISH_FAILED);
        command.setResultTime(now);
        command.setResultMessage("MQTT 发布尝试次数已耗尽");
        clearEncryptedPayload(command, now);
        wifiConfigLifecycleService.handleTerminalCommand(command);
        sessionCommandLifecycleService.handleTerminalCommand(command);
    }

    private UpdateWrapper<DeviceCommandRecord> ownedCommand(
            DeviceCommandRecord command) {
        return new UpdateWrapper<DeviceCommandRecord>()
                .eq("tenant_id", command.getTenantId())
                .eq("command_id", command.getCommandId())
                .eq("status", DeviceCommandStatus.PENDING);
    }

    private void requireSingleUpdate(
            UpdateWrapper<DeviceCommandRecord> update,
            String message) {
        if (commandRecordMapper.update(null, update) != 1) {
            throw new IllegalStateException(message);
        }
    }

    private void clearEncryptedPayload(
            DeviceCommandRecord command,
            LocalDateTime now) {
        commandRecordMapper.clearEncryptedPayload(
                command.getTenantId(), command.getCommandId(), now);
    }
}
