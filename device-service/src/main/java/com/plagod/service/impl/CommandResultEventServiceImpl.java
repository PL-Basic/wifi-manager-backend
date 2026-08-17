package com.plagod.service.impl;

import com.plagod.constant.DeviceCommandStatus;
import com.plagod.constant.DeviceCommandType;
import com.plagod.dto.CommandResultEvent;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.entity.device.Esp32Node;
import com.plagod.mapper.DeviceCommandRecordMapper;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.service.CommandResultEventService;
import com.plagod.service.DeviceWifiConfigLifecycleService;
import com.plagod.service.SessionCommandLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;


@Slf4j
@Service
public class CommandResultEventServiceImpl implements CommandResultEventService {

    @Autowired
    private DeviceCommandRecordMapper commandRecordMapper;
    @Autowired
    private Esp32NodeMapper nodeMapper;

    @Autowired
    private SessionCommandLifecycleService sessionCommandLifecycleService;

    @Autowired
    private DeviceWifiConfigLifecycleService wifiConfigLifecycleService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleCommandResult(CommandResultEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("命令结果不能为空");
        }

        if (event.getSuccess() == null) {
            throw new IllegalArgumentException("命令结果缺少 success");
        }

        String message;
        String deviceCode = cleanRequired(event.getDeviceCode(), 64, "命令结果缺少 deviceCode");
        String requestId = cleanRequestId(event.getRequestId());
        String commandType = normalizeCommandType(event.getType());

        if (DeviceCommandType.STAGE_WIFI_CONFIG.equals(commandType)) {
            message = Boolean.TRUE.equals(event.getSuccess()) ? "ESP32 已保存候选 WiFi 配置" : "ESP32 未能保存候选 WiFi 配置";
        } else {
            message = cleanNullable(event.getMessage(), 255);
        }


        Esp32Node node = nodeMapper.selectByDeviceCodeIncludeDeleted(deviceCode);
        if (node == null || !deviceCode.equals(node.getDeviceCode())) {
            throw new IllegalArgumentException("command-result 目标设备不存在");
        }

        DeviceCommandRecord command = commandRecordMapper.selectByRequestIdForUpdate(
                node.getTenantId(), requestId);

        // 未知 requestId 不能反向创建命令，否则会接受伪造结果。
        if (command == null) {
            log.warn("忽略未知命令结果，deviceCode={}, requestId={}, type={}", deviceCode, requestId, commandType);
            return;
        }

        if (!deviceCode.equals(command.getDeviceCode())) {
            throw new IllegalArgumentException("command-result 的设备编码与原命令不一致");
        }

        if (!commandType.equals(command.getCommandType())) {
            throw new IllegalArgumentException("command-result 的命令类型与原命令不一致");
        }

        int targetStatus = Boolean.TRUE.equals(event.getSuccess()) ? DeviceCommandStatus.SUCCEEDED : DeviceCommandStatus.EXECUTION_FAILED;

        // QoS 1 允许重复投递。终态记录不能被重复结果再次修改。
        if (DeviceCommandStatus.isTerminal(command.getStatus())) {
            if (Integer.valueOf(targetStatus).equals(command.getStatus())) {
                log.info("忽略重复命令结果，requestId={}, status={}", requestId, targetStatus);
            } else {
                log.warn("忽略与现有终态冲突的命令结果，requestId={}, oldStatus={}, newStatus={}", requestId, command.getStatus(), targetStatus);
            }
            commandRecordMapper.clearEncryptedPayload(
                    command.getTenantId(), command.getCommandId(), LocalDateTime.now());
            wifiConfigLifecycleService.handleTerminalCommand(command);
            sessionCommandLifecycleService.handleTerminalCommand(command);
            return;
        }

        boolean published =
                Integer.valueOf(DeviceCommandStatus.PUBLISHED)
                        .equals(command.getStatus());
        boolean claimedPublishWindow =
                Integer.valueOf(DeviceCommandStatus.PENDING)
                        .equals(command.getStatus())
                        && StringUtils.hasText(command.getDispatchWorkerId())
                        && command.getDispatchLeaseUntil() != null;
        if (!published && !claimedPublishWindow) {
            throw new IllegalStateException("命令尚未进入已发布状态，不能接收执行结果");
        }

        LocalDateTime now = LocalDateTime.now();

        command.setStatus(targetStatus);
        command.setResultTime(now);
        command.setResultMessage(message);
        command.setUpdateTime(now);

        if (commandRecordMapper.finalizeFromCommandResult(
                command.getCommandId(),
                command.getTenantId(),
                targetStatus,
                DeviceCommandStatus.PUBLISHED,
                DeviceCommandStatus.PENDING,
                now,
                message) != 1) {
            throw new IllegalStateException("命令结果保存失败");
        }
        commandRecordMapper.clearEncryptedPayload(
                command.getTenantId(), command.getCommandId(), now);
        wifiConfigLifecycleService.handleTerminalCommand(command);
        // 命令状态和 Session 状态必须在同一事务中提交。
        sessionCommandLifecycleService.handleTerminalCommand(command);
        log.info("命令结果保存成功，requestId={}, type={}, success={}", requestId, commandType, event.getSuccess());
    }


    private String normalizeCommandType(String value) {
        String type = cleanRequired(value, 32, "命令结果缺少 type").toUpperCase(Locale.ROOT);

        if (!DeviceCommandType.isTerminalType(type)) {
            throw new IllegalArgumentException("未知的 command-result 类型：" + type);
        }


        return type;
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

    private String cleanRequestId(String value) {
        String requestId = cleanRequired(value, 63, "命令结果缺少 requestId");

        if (requestId.getBytes(StandardCharsets.US_ASCII).length > 63) {
            throw new IllegalArgumentException("命令结果 requestId 长度超限");
        }
        for (int index = 0; index < requestId.length(); index++) {
            char current = requestId.charAt(index);
            if (current < 0x21 || current > 0x7E) {
                throw new IllegalArgumentException("命令结果 requestId 必须使用可见 ASCII 字符");
            }
        }
        return requestId;
    }

    private String cleanNullable(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String cleaned = value.trim();

        if (cleaned.length() > maxLength) {
            throw new IllegalArgumentException("命令结果说明长度超限");
        }

        return cleaned;
    }
}
