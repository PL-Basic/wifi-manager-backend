package com.plagod.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.constant.DeviceCommandPurpose;
import com.plagod.constant.MqttTopics;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.entity.device.Esp32Node;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.service.DeviceCommandOutboxService;
import com.plagod.service.ManagedDeviceCommandService;
import com.plagod.vo.device.DeviceCommandResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ManagedDeviceCommandServiceImpl implements ManagedDeviceCommandService {

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
    public DeviceCommandResult enqueueDisconnectMac(String deviceCode, String mac, Long alertId, String purpose) {

        if (!DeviceCommandPurpose.isDisconnectMacPurpose(purpose)) {
            throw new IllegalArgumentException("DISCONNECT_MAC 命令用途无效");
        }

        Esp32Node node = loadNode(deviceCode);
        String normalizedMac = normalizeMac(mac);
        if (normalizedMac == null) {
            throw new IllegalArgumentException("客户端 MAC 格式不正确");
        }

        String requestId = UUID.randomUUID().toString();
        String topic = MqttTopics.deviceDisconnectMac(node.getDeviceCode());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("mac", normalizedMac);
        // 固件当前要求 alertId 字段存在；手动命令使用 0。
        body.put("alertId", alertId == null ? 0L : alertId);

        return enqueue(node, requestId, "DISCONNECT_MAC", purpose, normalizedMac, alertId, topic, body);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceCommandResult enqueueBlockTraffic(String deviceCode, String dstIp, String sni, Long alertId, String purpose) {

        if (!DeviceCommandPurpose.isBlockTrafficPurpose(purpose)) {
            throw new IllegalArgumentException("BLOCK_TRAFFIC 命令用途无效");
        }

        Esp32Node node = loadNode(deviceCode);
        String cleanedDstIp = cleanRequired(dstIp, 15, "目标 IPv4 不能为空");
        if (!IPV4_PATTERN.matcher(cleanedDstIp).matches()) {
            throw new IllegalArgumentException("目标 IP 必须是合法 IPv4 地址");
        }
        String cleanedSni = cleanNullable(sni, 255);

        String requestId = UUID.randomUUID().toString();
        String topic = MqttTopics.deviceBlockTraffic(node.getDeviceCode());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("dstIp", cleanedDstIp);
        if (cleanedSni != null) {
            body.put("sni", cleanedSni);
        }
        body.put("alertId", alertId == null ? 0L : alertId);

        return enqueue(node, requestId, "BLOCK_TRAFFIC", purpose, null, alertId, topic, body);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceCommandResult enqueueKick(String deviceCode, String reason, String purpose) {
        if (!DeviceCommandPurpose.isKickPurpose(purpose)) {
            throw new IllegalArgumentException("KICK 命令用途无效");
        }

        Esp32Node node = loadNode(deviceCode);
        String cleanedReason = cleanKickReason(reason);

        String requestId = UUID.randomUUID().toString();
        String topic = MqttTopics.deviceKick(node.getDeviceCode());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("deviceCode", node.getDeviceCode());
        body.put("reason", cleanedReason == null ? "" : cleanedReason);

        return enqueue(node, requestId, "KICK", purpose, null, null, topic, body);
    }

    private DeviceCommandResult enqueue(Esp32Node node, String requestId, String commandType, String purpose, String mac, Long alertId, String topic, Map<String, Object> body) {

        try {
            String payload = objectMapper.writeValueAsString(body);

            DeviceCommandRecord command = new DeviceCommandRecord();
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

    private Esp32Node loadNode(String deviceCode) {
        String cleaned = cleanRequired(deviceCode, 64, "deviceCode 不能为空");

        Esp32Node node = esp32NodeMapper.selectByDeviceCodeIncludeDeleted(cleaned);

        if (node == null || Integer.valueOf(1).equals(node.getDelFlag())) {
            throw new IllegalArgumentException("命令目标 ESP32 不存在或已退役");
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

    // 固件当前使用 18 字节缓冲区保存 reason，需要保留一个字节给字符串结束符。

    private String cleanKickReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }

        String cleaned = reason.trim();

        if (cleaned.getBytes(StandardCharsets.UTF_8).length > 17) {
            throw new IllegalArgumentException(
                    "KICK reason 的 UTF-8 长度不能超过 17 字节");
        }

        return cleaned;
    }
}
