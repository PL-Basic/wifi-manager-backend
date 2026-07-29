package com.plagod.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.audit.Audited;
import com.plagod.constant.*;
import com.plagod.dto.device.WifiConfigStageDTO;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.entity.device.DeviceWifiConfigRecord;
import com.plagod.entity.device.Esp32Node;
import com.plagod.mapper.*;
import com.plagod.security.WifiCommandPayloadCrypto;
import com.plagod.service.*;
import com.plagod.vo.device.WifiConfigTaskVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;

@Service
public class DeviceWifiConfigServiceImpl implements DeviceWifiConfigService {

    private static final int NODE_ONLINE = 1;
    private static final long MAX_WIFI_CONFIG_VERSION = 0xFFFFFFFFL;

    @Autowired
    private Esp32NodeMapper esp32NodeMapper;
    @Autowired
    private DeviceWifiConfigRecordMapper wifiConfigRecordMapper;
    @Autowired
    private DeviceCommandOutboxService commandOutboxService;
    @Autowired
    private WifiCommandPayloadCrypto payloadCrypto;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${wifi.device.heartbeat-timeout-seconds:60}")
    private long heartbeatTimeoutSeconds;

    @Override
    @Audited(action = "device.wifi.stage", includeArgs = false)
    @Transactional(rollbackFor = Exception.class)
    public WifiConfigTaskVO stageCandidate(String deviceCode, WifiConfigStageDTO stageDTO) {

        String cleanDeviceCode = cleanRequired(deviceCode, 64, "deviceCode 不能为空");

        if (stageDTO == null) {
            throw new IllegalArgumentException("候选 WiFi 配置不能为空");
        }

        if (!payloadCrypto.isAvailable()) {
            throw new IllegalStateException("敏感设备命令密钥未配置，暂时不能下发候选 WiFi 配置");
        }

        String ssid = stageDTO.getSsid();
        String password = stageDTO.getPassword();
        validateCredentials(ssid, password);

        Esp32Node node = esp32NodeMapper.selectByDeviceCodeForUpdateIncludeDeleted(cleanDeviceCode);

        LocalDateTime now = LocalDateTime.now();
        validateOnlineNode(node, cleanDeviceCode, now);

        DeviceWifiConfigRecord latest = wifiConfigRecordMapper.selectLatestByNodeId(node.getNodeId());

        long configVersion = nextVersion(latest);

        if (latest != null && DeviceWifiConfigStatus.isReplaceable(latest.getStatus())) {

            int changed = wifiConfigRecordMapper.supersedeReplaceable(
                    latest.getWifiConfigId(),
                    DeviceWifiConfigStatus.STAGED,
                    DeviceWifiConfigStatus.UNKNOWN,
                    DeviceWifiConfigStatus.SUPERSEDED,
                    now);

            if (changed != 1) {
                throw new IllegalStateException("原候选配置状态已经变化，请刷新后重试");
            }
        }

        String requestId = UUID.randomUUID().toString();
        boolean passwordConfigured = !password.isEmpty();

        try {
            Map<String, Object> realBody = new LinkedHashMap<>();
            realBody.put("requestId", requestId);
            realBody.put("deviceCode", node.getDeviceCode());
            realBody.put("configVersion", configVersion);
            realBody.put("ssid", ssid);
            realBody.put("password", password);

            String realPayload = objectMapper.writeValueAsString(realBody);
            String encryptedPayload = payloadCrypto.encrypt(realPayload, requestId);

            Map<String, Object> safeBody = new LinkedHashMap<>();
            safeBody.put("requestId", requestId);
            safeBody.put("deviceCode", node.getDeviceCode());
            safeBody.put("configVersion", configVersion);
            safeBody.put("ssid", ssid);
            safeBody.put("passwordConfigured", passwordConfigured);

            String safePayload = objectMapper.writeValueAsString(safeBody);

            DeviceWifiConfigRecord task = new DeviceWifiConfigRecord();
            task.setNodeId(node.getNodeId());
            task.setDeviceCode(node.getDeviceCode());
            task.setRequestId(requestId);
            task.setConfigVersion(configVersion);
            task.setSsid(ssid);
            task.setPasswordConfigured(passwordConfigured);
            task.setStatus(DeviceWifiConfigStatus.DISPATCHING);
            task.setCreateTime(now);
            task.setUpdateTime(now);

            if (wifiConfigRecordMapper.insert(task) != 1 || task.getWifiConfigId() == null) {
                throw new IllegalStateException("候选 WiFi 任务创建失败");
            }

            DeviceCommandRecord command = new DeviceCommandRecord();
            command.setRequestId(requestId);
            command.setNodeId(node.getNodeId());
            command.setDeviceCode(node.getDeviceCode());
            command.setCommandType(DeviceCommandType.STAGE_WIFI_CONFIG);
            command.setPurpose(DeviceCommandPurpose.ADMIN_STAGE_WIFI_CONFIG);
            command.setTopic(MqttTopics.deviceStageWifiConfig(node.getDeviceCode()));
            command.setPayload(safePayload);
            command.setEncryptedPayload(encryptedPayload);

            commandOutboxService.enqueue(command);
            return toVO(task);

        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("候选 WiFi 命令序列化失败", exception);
        }
    }

    private long nextVersion(DeviceWifiConfigRecord latest) {
        if (latest == null) {
            return 1L;
        }

        if (!DeviceWifiConfigStatus.isKnown(latest.getStatus()) || latest.getConfigVersion() == null || latest.getConfigVersion() < 1) {
            throw new IllegalStateException("现有 WiFi 配置任务数据异常");
        }

        if (Integer.valueOf(DeviceWifiConfigStatus.DISPATCHING).equals(latest.getStatus())) {
            throw new IllegalStateException("设备已有正在下发的候选 WiFi 配置");
        }

        if (latest.getConfigVersion() >= MAX_WIFI_CONFIG_VERSION) {
            throw new IllegalStateException("WiFi 配置版本已经达到固件支持上限");
        }

        return latest.getConfigVersion() + 1;
    }

    private void validateOnlineNode(Esp32Node node, String deviceCode, LocalDateTime now) {

        if (node == null || Integer.valueOf(1).equals(node.getDelFlag())) {
            throw new IllegalArgumentException("目标 ESP32 不存在或已退役");
        }

        if (!deviceCode.equals(node.getDeviceCode())) {
            throw new IllegalArgumentException("deviceCode 与登记编码不完全一致");
        }

        if (heartbeatTimeoutSeconds < 10 || heartbeatTimeoutSeconds > 3600) {
            throw new IllegalStateException("设备心跳超时配置无效");
        }

        LocalDateTime cutoff = now.minusSeconds(heartbeatTimeoutSeconds);

        if (!Integer.valueOf(NODE_ONLINE).equals(node.getStatus()) || node.getLastHeartbeat() == null || !node.getLastHeartbeat().isAfter(cutoff)) {
            throw new IllegalStateException("设备当前离线或心跳已经过期，不能下发 WiFi 配置");
        }
    }

    private void validateCredentials(String ssid, String password) {
        if (ssid == null) {
            throw new IllegalArgumentException("SSID 不能为空");
        }

        int ssidBytes = ssid.getBytes(StandardCharsets.UTF_8).length;

        if (ssidBytes < 1 || ssidBytes > 32 || ssid.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("SSID 的 UTF-8 长度必须在 1 到 32 字节之间");
        }

        if (password == null) {
            throw new IllegalArgumentException("password 字段不能为空");
        }

        int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;

        if (passwordBytes != 0 && (passwordBytes < 8 || passwordBytes > 63)) {
            throw new IllegalArgumentException("WiFi 密码为空或 UTF-8 长度为 8 到 63 字节");
        }

        if (password.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("WiFi 密码不能包含空字符");
        }
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

    private WifiConfigTaskVO toVO(DeviceWifiConfigRecord record) {
        WifiConfigTaskVO vo = new WifiConfigTaskVO();
        BeanUtils.copyProperties(record, vo);
        vo.setStatusName(DeviceWifiConfigStatus.nameOf(record.getStatus()));
        return vo;
    }
}