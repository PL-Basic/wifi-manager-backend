package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.plagod.constant.DeviceCommandPurpose;
import com.plagod.constant.DeviceCommandStatus;
import com.plagod.constant.DeviceCommandType;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.mapper.DeviceCommandRecordMapper;
import com.plagod.mqtt.MqttCommandPublisher;
import com.plagod.security.WifiCommandPayloadCrypto;
import com.plagod.service.DeviceCommandDispatchService;
import com.plagod.service.DeviceCommandDispatchTransactionService;
import com.plagod.service.DeviceWifiConfigLifecycleService;
import com.plagod.service.SessionCommandLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class DeviceCommandDispatchServiceImpl implements DeviceCommandDispatchService {

    @Autowired
    private DeviceCommandRecordMapper commandRecordMapper;

    @Autowired
    private MqttCommandPublisher mqttCommandPublisher;

    @Autowired
    private DeviceCommandDispatchTransactionService dispatchTransactionService;

    @Autowired
    private SessionCommandLifecycleService sessionCommandLifecycleService;

    @Autowired
    private WifiCommandPayloadCrypto wifiCommandPayloadCrypto;

    @Autowired
    private DeviceWifiConfigLifecycleService wifiConfigLifecycleService;

    // 包含首次发布在内的最大发布次数。
    @Value("${wifi.command.publish-max-attempts:3}")
    private int publishMaxAttempts;

    @Value("${wifi.command.publish-retry-delay-seconds:3}")
    private long publishRetryDelaySeconds;

    @Value("${wifi.command.dispatch-lease-seconds:30}")
    private long dispatchLeaseSeconds;

    @Value("${wifi.command.result-timeout-seconds:15}")
    private long resultTimeoutSeconds;

    private String dispatchWorkerId =
            "device-command-" + UUID.randomUUID().toString();

    @Override
    public void dispatchOne(Long commandId) {
        validateConfiguration();

        if (commandId == null || commandId <= 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        DeviceCommandRecord command = dispatchTransactionService.claim(
                commandId,
                dispatchWorkerId,
                now,
                now.plusSeconds(dispatchLeaseSeconds),
                publishMaxAttempts,
                publishRetryDelaySeconds);
        if (command == null) {
            return;
        }

        try {
            String publishPayload = resolvePublishPayload(command);
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("MQTT 发布不能位于 Device 本地事务中");
            }
            mqttCommandPublisher.publish(command.getTopic(), publishPayload);
        } catch (Exception exception) {
            boolean finalized = dispatchTransactionService.finalizeFailure(
                    commandId,
                    dispatchWorkerId,
                    LocalDateTime.now(),
                    publishMaxAttempts,
                    publishRetryDelaySeconds);
            log.warn(
                    "设备命令发布失败，commandId={}, requestId={}, attempts={}, finalized={}, type={}",
                    command.getCommandId(),
                    command.getRequestId(),
                    command.getRetryCount(),
                    finalized,
                    exception.getClass().getName());
            return;
        }

        LocalDateTime publishedAt = LocalDateTime.now();
        boolean finalized = dispatchTransactionService.finalizePublished(
                commandId,
                dispatchWorkerId,
                publishedAt,
                publishedAt.plusSeconds(resultTimeoutSeconds));
        if (finalized) {
            log.info(
                    "设备命令发布成功，commandId={}, requestId={}, attempts={}",
                    commandId,
                    command.getRequestId(),
                    command.getRetryCount());
        } else {
            log.warn(
                    "设备命令已发布但 claim 不再归当前 worker，等待同一 requestId 重投，commandId={}, requestId={}",
                    commandId,
                    command.getRequestId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void timeoutOne(Long commandId) {
        if (commandId == null || commandId <= 0) {
            return;
        }

        DeviceCommandRecord command = commandRecordMapper.selectByCommandIdForUpdate(commandId);

        if (command == null || !Integer.valueOf(DeviceCommandStatus.PUBLISHED).equals(command.getStatus())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (command.getDeadlineTime() == null || command.getDeadlineTime().isAfter(now)) {
            return;
        }

        command.setStatus(DeviceCommandStatus.TIMED_OUT);
        command.setResultTime(now);
        command.setResultMessage("等待 ESP32 command-result 超时");
        command.setUpdateTime(now);

        // 先持久化命令终态。
        save(command);
        clearEncryptedPayload(command, now);
        wifiConfigLifecycleService.handleTerminalCommand(command);
        // TIMED_OUT 命令和 Session 关闭处于同一事务。
        sessionCommandLifecycleService.handleTerminalCommand(command);

        log.warn("设备命令结果超时，commandId={}, requestId={}", commandId, command.getRequestId());
    }

    // 显式保存命令运行状态。
    private void save(DeviceCommandRecord command) {
        if (command == null || command.getCommandId() == null) {
            throw new IllegalArgumentException("待保存命令及 commandId 不能为空");
        }

        UpdateWrapper<DeviceCommandRecord> update = new UpdateWrapper<>();

        update.eq("tenant_id", command.getTenantId())
                .eq("command_id", command.getCommandId())
                .set("status", command.getStatus())
                .set("retry_count", command.getRetryCount())
                .set("next_retry_time", command.getNextRetryTime())
                .set("publish_time", command.getPublishTime())
                .set("deadline_time", command.getDeadlineTime())
                .set("result_time", command.getResultTime())
                .set("result_message", command.getResultMessage())
                .set("update_time", command.getUpdateTime());

        if (commandRecordMapper.update(null, update) != 1) {
            throw new IllegalStateException("设备命令状态保存失败");
        }
    }

    private void validateConfiguration() {
        if (publishMaxAttempts < 1 || publishMaxAttempts > 10) {
            throw new IllegalStateException("MQTT 最大发布次数必须在 1 到 10 之间");
        }
        if (publishRetryDelaySeconds < 1) {
            throw new IllegalStateException("MQTT 发布重试间隔必须大于 0");
        }
        if (dispatchLeaseSeconds < 1) {
            throw new IllegalStateException("MQTT dispatch lease 必须大于 0");
        }
        if (resultTimeoutSeconds < 1) {
            throw new IllegalStateException("command-result 超时时间必须大于 0");
        }
    }

    private String resolvePublishPayload(DeviceCommandRecord command) {

        boolean sensitiveType = DeviceCommandType.isSensitiveType(command.getCommandType());

        boolean sensitivePurpose = DeviceCommandPurpose.isSensitivePurpose(command.getPurpose());

        if (sensitiveType != sensitivePurpose) {
            throw new IllegalStateException("敏感命令的 commandType 与 purpose 不匹配");
        }

        if (!sensitiveType) {
            return command.getPayload();
        }

        if (!StringUtils.hasText(command.getEncryptedPayload())) {
            throw new IllegalStateException("敏感命令缺少加密载荷");
        }

        return wifiCommandPayloadCrypto.decrypt(command.getEncryptedPayload(), command.getRequestId());
    }

    private void clearEncryptedPayload(DeviceCommandRecord command, LocalDateTime now) {

        if (command == null || command.getCommandId() == null) {
            return;
        }

        commandRecordMapper.clearEncryptedPayload(
                command.getTenantId(), command.getCommandId(), now);
    }
}
