package com.plagod.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.constant.DeviceCommandPurpose;
import com.plagod.constant.DeviceCommandType;
import com.plagod.constant.MqttTopics;
import com.plagod.dto.BlockTrafficCommand;
import com.plagod.dto.DisconnectMacCommand;
import com.plagod.dto.KickCommand;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.entity.device.Esp32Node;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.service.DeviceCommandOutboxService;
import com.plagod.service.ManagedDeviceCommandService;
import com.plagod.utils.TenantScopeUtils;
import com.plagod.vo.device.DeviceCommandResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ManagedDeviceCommandServiceImpl implements ManagedDeviceCommandService {

    private static final int KICK_REASON_MAX_UTF8_BYTES = 255;
    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");
    private static final Pattern IPV4_PATTERN = Pattern.compile("^(?:(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])$");

    @Autowired
    private Esp32NodeMapper esp32NodeMapper;
    @Autowired
    private DeviceCommandOutboxService commandOutboxService;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceCommandResult enqueueDisconnectMac(Long tenantId, String deviceCode, String mac, Long alertId, String purpose) {

        if (!DeviceCommandPurpose.isDisconnectMacPurpose(purpose)) {
            throw new IllegalArgumentException("DISCONNECT_MAC 命令用途无效");
        }

        Esp32Node node = loadNode(tenantId, deviceCode);
        String normalizedMac = normalizeMac(mac);
        if (normalizedMac == null) {
            throw new IllegalArgumentException("客户端 MAC 格式不正确");
        }

        String requestId = UUID.randomUUID().toString();
        String topic = MqttTopics.deviceDisconnectMac(node.getDeviceCode());

        // 固件当前要求 alertId 字段存在；手动命令使用 0。
        DisconnectMacCommand body = new DisconnectMacCommand(
                requestId,
                normalizedMac,
                alertId == null ? 0L : alertId);

        return enqueue(node, requestId, DeviceCommandType.DISCONNECT_MAC, purpose, normalizedMac, alertId, topic, body);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceCommandResult enqueueBlockTraffic(Long tenantId, String deviceCode, String dstIp, String sni, Long alertId, String purpose) {

        if (!DeviceCommandPurpose.isBlockTrafficPurpose(purpose)) {
            throw new IllegalArgumentException("BLOCK_TRAFFIC 命令用途无效");
        }

        Esp32Node node = loadNode(tenantId, deviceCode);
        String cleanedDstIp = cleanRequired(dstIp, 15, "目标 IPv4 不能为空");
        if (!IPV4_PATTERN.matcher(cleanedDstIp).matches()) {
            throw new IllegalArgumentException("目标 IP 必须是合法 IPv4 地址");
        }
        String cleanedSni = cleanNullable(sni, 255);

        String requestId = UUID.randomUUID().toString();
        String topic = MqttTopics.deviceBlockTraffic(node.getDeviceCode());

        BlockTrafficCommand body = new BlockTrafficCommand(
                requestId,
                cleanedDstIp,
                cleanedSni,
                alertId == null ? 0L : alertId);

        return enqueue(node, requestId, DeviceCommandType.BLOCK_TRAFFIC, purpose, null, alertId, topic, body);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceCommandResult enqueueKick(Long tenantId, String deviceCode, String reason, String purpose) {
        if (!DeviceCommandPurpose.isKickPurpose(purpose)) {
            throw new IllegalArgumentException("KICK 命令用途无效");
        }

        Esp32Node node = loadNode(tenantId, deviceCode);
        String cleanedReason = cleanKickReason(reason);

        String requestId = UUID.randomUUID().toString();
        String topic = MqttTopics.deviceKick(node.getDeviceCode());

        KickCommand body = new KickCommand(
                requestId,
                node.getDeviceCode(),
                cleanedReason == null ? "" : cleanedReason);

        return enqueue(node, requestId, DeviceCommandType.KICK, purpose, null, null, topic, body);
    }

    private DeviceCommandResult enqueue(Esp32Node node, String requestId, String commandType, String purpose, String mac, Long alertId, String topic, Object body) {

        try {
            String payload = objectMapper.writeValueAsString(body);

            DeviceCommandRecord command = new DeviceCommandRecord();
            command.setTenantId(node.getTenantId());
            command.setRequestId(requestId);
            command.setNodeId(node.getNodeId());
            command.setDeviceCode(node.getDeviceCode());
            command.setCommandType(commandType);
            command.setPurpose(purpose);
            command.setMac(mac);
            command.setAlertId(alertId);
            command.setTopic(topic);
            command.setPayload(payload);

            commandOutboxService.enqueue(command);
            return new DeviceCommandResult(requestId, topic, payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(commandType + " 命令序列化失败", exception);
        }
    }

    private Esp32Node loadNode(Long tenantId, String deviceCode) {
        TenantScopeUtils.requireTenantId(tenantId);
        String cleaned = cleanRequired(deviceCode, 64, "deviceCode 不能为空");

        Esp32Node node = esp32NodeMapper.selectByDeviceCodeAndTenantIncludeDeleted(tenantId, cleaned);

        if (node == null || Integer.valueOf(1).equals(node.getDelFlag())) {
            throw ApiStatusException.notFound("命令目标 ESP32 不存在或已退役");
        }
        return node;
    }

    private String normalizeMac(String mac) {
        if (!StringUtils.hasText(mac)) {
            return null;
        }

        String normalized = mac.trim().toUpperCase(Locale.ROOT);
        return MAC_PATTERN.matcher(normalized).matches() ? normalized : null;
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

    private String cleanNullable(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String cleaned = value.trim();
        if (cleaned.length() > maxLength) {
            throw new IllegalArgumentException("可选参数长度超限");
        }
        return cleaned;
    }

    private String cleanKickReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }

        String cleaned = reason.trim();

        if (cleaned.getBytes(StandardCharsets.UTF_8).length > KICK_REASON_MAX_UTF8_BYTES) {
            throw new IllegalArgumentException(
                    "KICK reason 的 UTF-8 长度不能超过255字节");
        }

        return cleaned;
    }
}
