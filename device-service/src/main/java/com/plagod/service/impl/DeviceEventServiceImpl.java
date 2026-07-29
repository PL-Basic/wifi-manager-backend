package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.plagod.dto.DeviceStatusEvent;
import com.plagod.entity.Esp32Node;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.service.DeviceEventService;
import com.plagod.service.DeviceWifiConfigLifecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class DeviceEventServiceImpl implements DeviceEventService {

    private static final int NODE_ONLINE = 1;

    @Autowired
    private Esp32NodeMapper esp32NodeMapper;

    @Autowired
    private DeviceWifiConfigLifecycleService wifiConfigLifecycleService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleStatusEvent(DeviceStatusEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("设备状态事件不能为空");
        }

        String deviceCode = cleanRequired(event.getDeviceCode(), 64, "设备状态事件缺少 deviceCode");

        // 与管理员创建候选配置保持相同的“节点 -> 配置任务”锁顺序。
        Esp32Node node = esp32NodeMapper.selectByDeviceCodeForUpdateIncludeDeleted(deviceCode);

        // MQTT 状态上报不能反向创建未登记节点。
        if (node == null) {
            throw new IllegalArgumentException("上报状态的 ESP32 节点尚未登记");
        }

        // 数据库排序规则可能不区分大小写，应用层仍要求精确匹配。
        if (!deviceCode.equals(node.getDeviceCode())) {
            throw new IllegalArgumentException("MQTT topic 中的 deviceCode 与登记编码不完全一致");
        }

        if (Integer.valueOf(1).equals(node.getDelFlag())) {
            throw new IllegalArgumentException("设备已退役，拒绝状态上报");
        }

        if (event.getCurrentClients() != null
                && event.getCurrentClients() < 0) {
            throw new IllegalArgumentException("当前客户端数量不能为负数");
        }

        String ip = cleanNullable(event.getIp(), 45, "设备 IP");
        String firmwareVersion = cleanNullable(event.getFirmwareVersion(), 32, "固件版本");
        String wifiStatus = cleanNullable(event.getWifiStatus(), 32, "WiFi 状态");

        LocalDateTime heartbeatTime = LocalDateTime.now();

        UpdateWrapper<Esp32Node> update = new UpdateWrapper<>();

        update.eq("node_id", node.getNodeId())
                .eq("del_flag", 0)
                .set(StringUtils.hasText(ip), "ip", ip)
                .set(StringUtils.hasText(firmwareVersion), "firmware_version", firmwareVersion)
                .set(StringUtils.hasText(wifiStatus), "wifi_status", wifiStatus)
                .set(event.getCurrentClients() != null, "current_clients", event.getCurrentClients())
                .set("status", NODE_ONLINE)
                .set("last_heartbeat", heartbeatTime)
                .set("update_time", heartbeatTime);

        /*
         * del_flag 条件防止节点在读取后被并发退役，
         * 心跳不能把退役节点重新激活。
         */
        if (esp32NodeMapper.update(null, update) != 1) {
            throw new IllegalStateException("设备状态更新失败，节点可能已经退役");
        }
        wifiConfigLifecycleService.handleStatusEvent(node, event, heartbeatTime);
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

    private String cleanNullable(String value, int maxLength, String fieldName) {

        if (!StringUtils.hasText(value)) {
            return null;
        }

        String cleaned = value.trim();

        if (cleaned.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度超限");
        }

        return cleaned;
    }
}