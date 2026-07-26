package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.plagod.constant.DeviceCommandStatus;
import com.plagod.entity.DeviceCommandRecord;
import com.plagod.mapper.DeviceCommandRecordMapper;
import com.plagod.mqtt.MqttCommandPublisher;
import com.plagod.service.DeviceCommandDispatchService;
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

        try {
            // try 只捕获真正的 MQTT 发布异常。
            mqttCommandPublisher.publish(command.getTopic(), command.getPayload());
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
        // TIMED_OUT 命令和 Session 关闭处于同一事务。
        sessionCommandLifecycleService.handleTerminalCommand(command);

        log.warn("设备命令结果超时，commandId={}, requestId={}", commandId, command.getRequestId());
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

        // 普通重试仍为 PENDING，不能关闭 Session。
        // 只有最终发布失败才驱动 Session 关闭。
        if (Integer.valueOf(DeviceCommandStatus.PUBLISH_FAILED).equals(command.getStatus())) {
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
}