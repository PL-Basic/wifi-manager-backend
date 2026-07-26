package com.plagod.service.impl;

import com.plagod.constant.DeviceCommandStatus;
import com.plagod.entity.DeviceCommandRecord;
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
            throw new IllegalArgumentException("命令 payload 不能为空");
        }

        LocalDateTime now = LocalDateTime.now();

        // 强制初始化 Outbox 状态，不信任调用方传入的运行结果字段。
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