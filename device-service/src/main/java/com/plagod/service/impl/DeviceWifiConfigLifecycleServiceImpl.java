package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.plagod.constant.*;
import com.plagod.dto.DeviceStatusEvent;
import com.plagod.entity.*;
import com.plagod.mapper.DeviceWifiConfigRecordMapper;
import com.plagod.service.DeviceWifiConfigLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
public class DeviceWifiConfigLifecycleServiceImpl implements DeviceWifiConfigLifecycleService {

    private static final long MAX_CONFIG_VERSION = 0xFFFFFFFFL;

    @Autowired
    private DeviceWifiConfigRecordMapper wifiConfigRecordMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void handleTerminalCommand(DeviceCommandRecord command) {
        if (command == null) {
            return;
        }

        boolean sensitiveType = DeviceCommandType.isSensitiveType(command.getCommandType());
        boolean sensitivePurpose = DeviceCommandPurpose.isSensitivePurpose(command.getPurpose());

        if (!sensitiveType && !sensitivePurpose) {
            return;
        }

        if (sensitiveType != sensitivePurpose) {
            throw new IllegalStateException("WiFi 配置命令的 type 与 purpose 不匹配");
        }

        if (!DeviceCommandStatus.isTerminal(command.getStatus()) || !StringUtils.hasText(command.getRequestId())) {
            throw new IllegalStateException("WiFi 配置命令缺少终态或 requestId");
        }

        DeviceWifiConfigRecord task = wifiConfigRecordMapper.selectByRequestIdForUpdate(command.getRequestId());

        if (task == null) {
            throw new IllegalStateException("WiFi 配置命令缺少对应任务");
        }

        validateCommandTask(command, task);

        LocalDateTime now = command.getResultTime() == null ? LocalDateTime.now() : command.getResultTime();

        if (Integer.valueOf(DeviceCommandStatus.SUCCEEDED).equals(command.getStatus())) {
            updateFromDispatching(task, DeviceWifiConfigStatus.STAGED, null, now);
            return;
        }

        if (Integer.valueOf(DeviceCommandStatus.EXECUTION_FAILED).equals(command.getStatus())) {
            updateFromDispatching(task, DeviceWifiConfigStatus.FAILED, "ESP32 未能保存候选 WiFi 配置", now);
            return;
        }

        if (Integer.valueOf(DeviceCommandStatus.PUBLISH_FAILED).equals(command.getStatus())) {
            updateFromDispatching(task, DeviceWifiConfigStatus.FAILED, "候选 WiFi 配置命令发布失败", now);
            return;
        }

        if (Integer.valueOf(DeviceCommandStatus.TIMED_OUT).equals(command.getStatus())) {
            updateFromDispatching(task, DeviceWifiConfigStatus.UNKNOWN, "等待 ESP32 配置结果超时", now);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void handleStatusEvent(Esp32Node node, DeviceStatusEvent event, LocalDateTime heartbeatTime) {

        if (node == null || event == null || heartbeatTime == null) {
            throw new IllegalArgumentException("WiFi 配置心跳上下文不能为空");
        }

        ConfigReference active = parseReference(event.getActiveWifiConfigRequestId(), event.getActiveWifiConfigVersion(), "active");
        ConfigReference pending = parseReference(event.getPendingWifiConfigRequestId(), event.getPendingWifiConfigVersion(), "pending");

        if (active != null && pending != null && active.sameAs(pending)) {
            throw new IllegalArgumentException("active 与 pending WiFi 配置不能相同");
        }

        if (active != null) {
            applyActive(node, active, heartbeatTime);
        }

        if (pending != null) {
            applyPending(node, pending, heartbeatTime);
        }
    }

    private void applyPending(Esp32Node node, ConfigReference reference, LocalDateTime now) {

        DeviceWifiConfigRecord task = loadAndValidate(node, reference);

        if (task == null || Integer.valueOf(DeviceWifiConfigStatus.STAGED).equals(task.getStatus()) || Integer.valueOf(DeviceWifiConfigStatus.ACTIVE).equals(task.getStatus())) {
            return;
        }

        if (!Integer.valueOf(DeviceWifiConfigStatus.DISPATCHING).equals(task.getStatus()) && !Integer.valueOf(DeviceWifiConfigStatus.UNKNOWN).equals(task.getStatus())) {
            log.info("忽略不能恢复为 STAGED 的 WiFi 配置心跳，requestId={}, status={}", task.getRequestId(), task.getStatus());
            return;
        }

        UpdateWrapper<DeviceWifiConfigRecord> update = new UpdateWrapper<>();

        update.eq("wifi_config_id", task.getWifiConfigId())
                .in("status", DeviceWifiConfigStatus.DISPATCHING, DeviceWifiConfigStatus.UNKNOWN)
                .set("status", DeviceWifiConfigStatus.STAGED)
                .set("staged_time", task.getStagedTime() == null ? now : task.getStagedTime())
                .set("failure_message", null)
                .set("update_time", now);

        if (wifiConfigRecordMapper.update(null, update) != 1) {
            throw new IllegalStateException("WiFi 配置 STAGED 状态保存失败");
        }
    }

    private void applyActive(Esp32Node node, ConfigReference reference, LocalDateTime now) {

        DeviceWifiConfigRecord task = loadAndValidate(node, reference);

        if (task == null) {
            return;
        }

        if (!Integer.valueOf(DeviceWifiConfigStatus.ACTIVE).equals(task.getStatus())) {

            if (!Integer.valueOf(DeviceWifiConfigStatus.DISPATCHING).equals(task.getStatus())
                    && !Integer.valueOf(DeviceWifiConfigStatus.STAGED).equals(task.getStatus())
                    && !Integer.valueOf(DeviceWifiConfigStatus.UNKNOWN).equals(task.getStatus())) {
                log.info("忽略不能恢复为 ACTIVE 的 WiFi 配置心跳，requestId={}, status={}", task.getRequestId(), task.getStatus());
                return;
            }

            UpdateWrapper<DeviceWifiConfigRecord> update = new UpdateWrapper<>();

            update.eq("wifi_config_id", task.getWifiConfigId())
                    .in("status", DeviceWifiConfigStatus.DISPATCHING, DeviceWifiConfigStatus.STAGED, DeviceWifiConfigStatus.UNKNOWN)
                    .set("status", DeviceWifiConfigStatus.ACTIVE)
                    .set("staged_time", task.getStagedTime() == null ? now : task.getStagedTime())
                    .set("activated_time", now)
                    .set("failure_message", null)
                    .set("update_time", now);

            if (wifiConfigRecordMapper.update(null, update) != 1) {
                throw new IllegalStateException("WiFi 配置 ACTIVE 状态保存失败");
            }
        }

        // 新配置真正激活后，旧的当前配置才失去 ACTIVE 身份。
        UpdateWrapper<DeviceWifiConfigRecord> older = new UpdateWrapper<>();
        older.eq("node_id", node.getNodeId())
                .lt("config_version", reference.configVersion)
                .in("status", DeviceWifiConfigStatus.ACTIVE, DeviceWifiConfigStatus.STAGED, DeviceWifiConfigStatus.UNKNOWN)
                .set("status", DeviceWifiConfigStatus.SUPERSEDED)
                .set("update_time", now);

        wifiConfigRecordMapper.update(null, older);
    }

    private void updateFromDispatching(DeviceWifiConfigRecord task, int targetStatus, String failureMessage, LocalDateTime now) {

        if (Integer.valueOf(targetStatus).equals(task.getStatus())) {
            return;
        }

        // 心跳已经证明 STAGED/ACTIVE 时，迟到的失败或超时不能回退状态。
        if (!Integer.valueOf(DeviceWifiConfigStatus.DISPATCHING).equals(task.getStatus())) {
            return;
        }

        UpdateWrapper<DeviceWifiConfigRecord> update = new UpdateWrapper<>();

        update.eq("wifi_config_id", task.getWifiConfigId())
                .eq("status", DeviceWifiConfigStatus.DISPATCHING)
                .set("status", targetStatus)
                .set(targetStatus == DeviceWifiConfigStatus.STAGED, "staged_time", now)
                .set("failure_message", failureMessage)
                .set("update_time", now);

        if (wifiConfigRecordMapper.update(null, update) != 1) {
            throw new IllegalStateException("WiFi 配置命令终态保存失败");
        }
    }

    private DeviceWifiConfigRecord loadAndValidate(Esp32Node node, ConfigReference reference) {

        DeviceWifiConfigRecord task = wifiConfigRecordMapper.selectByRequestIdForUpdate(reference.requestId);

        if (task == null) {
            log.warn("忽略未知 WiFi 配置心跳，deviceCode={}, requestId={}, version={}", node.getDeviceCode(), reference.requestId, reference.configVersion);
            return null;
        }

        if (!reference.requestId.equals(task.getRequestId())
                || !node.getNodeId().equals(task.getNodeId())
                || !node.getDeviceCode().equals(task.getDeviceCode())
                || !Long.valueOf(reference.configVersion).equals(task.getConfigVersion())) {
            throw new IllegalArgumentException("WiFi 配置心跳与后端任务不匹配");
        }

        return task;
    }

    private void validateCommandTask(DeviceCommandRecord command, DeviceWifiConfigRecord task) {

        if (!command.getRequestId().equals(task.getRequestId()) || command.getNodeId() == null || !command.getNodeId().equals(task.getNodeId()) || !command.getDeviceCode().equals(task.getDeviceCode())) {
            throw new IllegalStateException(
                    "WiFi 配置命令与任务关联不一致");
        }
    }

    private ConfigReference parseReference(String requestId, Long configVersion, String name) {

        boolean hasRequestId = StringUtils.hasText(requestId);
        boolean emptyVersion = configVersion == null || configVersion == 0L;

        if (!hasRequestId && emptyVersion) {
            return null;
        }

        if (!hasRequestId || configVersion == null || configVersion < 1 || configVersion > MAX_CONFIG_VERSION) {
            throw new IllegalArgumentException(name + " WiFi 配置引用不完整");
        }

        String cleanedRequestId = requestId.trim();

        if (cleanedRequestId.length() > 64) {
            throw new IllegalArgumentException(name + " WiFi 配置 requestId 长度超限");
        }

        return new ConfigReference(cleanedRequestId, configVersion);
    }

    private static final class ConfigReference {

        private final String requestId;
        private final long configVersion;

        private ConfigReference(String requestId, long configVersion) {
            this.requestId = requestId;
            this.configVersion = configVersion;
        }

        private boolean sameAs(ConfigReference other) {
            return other != null && requestId.equals(other.requestId) && configVersion == other.configVersion;
        }
    }
}