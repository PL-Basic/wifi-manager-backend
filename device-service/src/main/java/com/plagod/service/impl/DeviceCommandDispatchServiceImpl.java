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
import com.plagod.service.DeviceWifiConfigLifecycleService;
import com.plagod.service.SessionCommandLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
public class DeviceCommandDispatchServiceImpl implements DeviceCommandDispatchService {

    @Autowired
    private DeviceCommandRecordMapper commandRecordMapper;

    @Autowired
    private MqttCommandPublisher mqttCommandPublisher;

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

    @Value("${wifi.command.result-timeout-seconds:15}")
    private long resultTimeoutSeconds;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispatchOne(Long commandId) {
        validateConfiguration();

        if (commandId == null || commandId <= 0) {
            return;
        }

        DeviceCommandRecord command = commandRecordMapper.selectByCommandIdForUpdate(commandId);

        if (command == null || !Integer.valueOf(DeviceCommandStatus.PENDING).equals(command.getStatus())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (command.getNextRetryTime() != null && command.getNextRetryTime().isAfter(now)) {
            return;
        }

        if (DeviceCommandType.isSensitiveType(command.getCommandType()) && DeviceCommandPurpose.isSensitivePurpose(command.getPurpose()) && !wifiCommandPayloadCrypto.isAvailable()) {

            deferUnavailableSensitiveCommand(command, now);
            return;
        }

        // 同一 Session 的旧 ALLOW 尚未发布时，REVOKE 必须等待。
        // 否则多实例调度可能让撤销命令先到 Broker，随后旧 ALLOW 又重新授权客户端。
        if (shouldWaitForEarlierSessionAllow(command)) {
            return;
        }

        try {
            String publishPayload = resolvePublishPayload(command);
            mqttCommandPublisher.publish(command.getTopic(), publishPayload);

        } catch (Exception exception) {
            handlePublishFailure(command, exception);
            return;
        }

        // 保存失败必须向外抛出并回滚，不能伪装成 MQTT 发布失败。
        LocalDateTime publishedAt = LocalDateTime.now();
        command.setStatus(DeviceCommandStatus.PUBLISHED);
        command.setPublishTime(publishedAt);
        command.setDeadlineTime(publishedAt.plusSeconds(resultTimeoutSeconds));
        command.setNextRetryTime(null);
        command.setResultMessage(null);
        command.setUpdateTime(publishedAt);

        save(command);
        log.info("设备命令发布成功，commandId={}, requestId={}", commandId, command.getRequestId());
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

    /**
     * 密钥暂不可用时延后敏感命令，不消耗发布重试次数。
     * 这样既保留待发送命令，也不会长期占据 Outbox 扫描批次。
     */
    private void deferUnavailableSensitiveCommand(DeviceCommandRecord command, LocalDateTime now) {

        command.setNextRetryTime(now.plusSeconds(publishRetryDelaySeconds));
        command.setResultMessage("敏感命令功能暂不可用，等待密钥配置");
        command.setUpdateTime(now);

        save(command);
    }

    private void handlePublishFailure(DeviceCommandRecord command, Exception exception) {

        LocalDateTime failedAt = LocalDateTime.now();
        int failedAttempts = command.getRetryCount() == null ? 1 : command.getRetryCount() + 1;

        command.setRetryCount(failedAttempts);
        command.setPublishTime(null);
        command.setDeadlineTime(null);
        command.setResultMessage(cleanErrorMessage(exception));
        command.setUpdateTime(failedAt);

        if (failedAttempts >= publishMaxAttempts) {
            command.setStatus(DeviceCommandStatus.PUBLISH_FAILED);
            command.setNextRetryTime(null);
            command.setResultTime(failedAt);
        } else {
            command.setStatus(DeviceCommandStatus.PENDING);
            command.setNextRetryTime(failedAt.plusSeconds(publishRetryDelaySeconds));
            command.setResultTime(null);
        }

        save(command);
        if (Integer.valueOf(DeviceCommandStatus.PUBLISH_FAILED).equals(command.getStatus())) {

            clearEncryptedPayload(command, failedAt);
            wifiConfigLifecycleService.handleTerminalCommand(command);
            sessionCommandLifecycleService.handleTerminalCommand(command);
        }
        log.warn("设备命令发布失败，commandId={}, requestId={}, attempts={}", command.getCommandId(), command.getRequestId(), failedAttempts, exception);
    }

    // 显式保存命令运行状态。
    private void save(DeviceCommandRecord command) {
        if (command == null || command.getCommandId() == null) {
            throw new IllegalArgumentException("待保存命令及 commandId 不能为空");
        }

        UpdateWrapper<DeviceCommandRecord> update = new UpdateWrapper<>();

        update.eq("command_id", command.getCommandId())
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

    private String cleanErrorMessage(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        if (!StringUtils.hasText(message)) {
            message = exception == null ? "MQTT 发布失败" : exception.getClass().getSimpleName();
        }

        message = message.trim();
        return message.length() <= 255 ? message : message.substring(0, 255);
    }

    private void validateConfiguration() {
        if (publishMaxAttempts < 1 || publishMaxAttempts > 10) {
            throw new IllegalStateException("MQTT 最大发布次数必须在 1 到 10 之间");
        }
        if (publishRetryDelaySeconds < 1) {
            throw new IllegalStateException("MQTT 发布重试间隔必须大于 0");
        }
        if (resultTimeoutSeconds < 1) {
            throw new IllegalStateException("command-result 超时时间必须大于 0");
        }
    }

    private boolean shouldWaitForEarlierSessionAllow(DeviceCommandRecord command) {
        if (!"REVOKE_ACCESS".equals(command.getCommandType()) || !DeviceCommandPurpose.isSessionRevokePurpose(command.getPurpose()) || command.getSessionId() == null || command.getSessionId() <= 0) {
            return false;
        }
        long pendingAllowCount = commandRecordMapper.countEarlierPendingSessionAllowCommands(command.getSessionId(), command.getCommandId(), DeviceCommandStatus.PENDING);

        return pendingAllowCount > 0;
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

        commandRecordMapper.clearEncryptedPayload(command.getCommandId(), now);
    }
}