package com.plagod.service.impl;

import com.plagod.constant.DeviceCommandPurpose;
import com.plagod.constant.DeviceCommandStatus;
import com.plagod.constant.DeviceCommandType;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.mapper.DeviceCommandRecordMapper;
import com.plagod.service.DeviceCommandOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
public class DeviceCommandOutboxServiceImpl implements DeviceCommandOutboxService {

    @Autowired
    private DeviceCommandRecordMapper commandRecordMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Long enqueue(DeviceCommandRecord command) {
        if (command == null) {
            throw new IllegalArgumentException("待入队命令不能为空");
        }

        if (command.getNodeId() == null || command.getNodeId() <= 0) {
            throw new IllegalArgumentException("命令缺少有效 nodeId");
        }

        command.setRequestId(cleanRequired(command.getRequestId(), 64, "命令缺少 requestId"));
        command.setDeviceCode(cleanRequired(command.getDeviceCode(), 64, "命令缺少 deviceCode"));
        command.setCommandType(cleanRequired(command.getCommandType(), 32, "命令缺少 commandType").toUpperCase(Locale.ROOT));
        command.setPurpose(cleanRequired(command.getPurpose(), 32, "命令缺少 purpose").toUpperCase(Locale.ROOT));
        command.setTopic(cleanRequired(command.getTopic(), 191, "命令缺少 MQTT topic"));

        if (!StringUtils.hasText(command.getPayload())) {
            throw new IllegalArgumentException("命令脱敏 payload 不能为空");
        }

        boolean sensitiveType = DeviceCommandType.isSensitiveType(command.getCommandType());

        boolean sensitivePurpose = DeviceCommandPurpose.isSensitivePurpose(command.getPurpose());

        /*
         * 命令类型决定固件动作，purpose 决定后端业务来源。
         * 两者必须同时声明敏感，防止错误组合绕过加密发布。
         */
        if (sensitiveType != sensitivePurpose) {
            throw new IllegalArgumentException("敏感命令的 commandType 与 purpose 不匹配");
        }

        if (sensitiveType && !StringUtils.hasText(command.getEncryptedPayload())) {
            throw new IllegalArgumentException("敏感命令缺少加密载荷");
        }

        if (!sensitiveType) {
            command.setEncryptedPayload(null);
        }

        LocalDateTime now = LocalDateTime.now();

        command.setCommandId(null);
        command.setStatus(DeviceCommandStatus.PENDING);
        command.setRetryCount(0);
        command.setNextRetryTime(now);
        command.setPublishTime(null);
        command.setDeadlineTime(null);
        command.setResultTime(null);
        command.setResultMessage(null);
        command.setCreateTime(now);
        command.setUpdateTime(now);

        if (commandRecordMapper.insert(command) != 1 || command.getCommandId() == null) {
            throw new IllegalStateException("设备命令入队失败");
        }

        // 只记录命令元数据，绝不记录普通或加密 payload。
        log.info("设备命令已进入 Outbox，commandId={}, requestId={}, type={}, purpose={}", command.getCommandId(), command.getRequestId(), command.getCommandType(), command.getPurpose());

        return command.getCommandId();
    }

    private String cleanRequired(String value, int maxLength, String message) {

        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }

        String cleaned = value.trim();

        if (cleaned.length() > maxLength) {
            throw new IllegalArgumentException(message + "，长度超限");
        }

        return cleaned;
    }
}