package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.plagod.dto.DeviceStatusEvent;
import com.plagod.entity.Esp32Node;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.service.DeviceEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class DeviceEventServiceImpl implements DeviceEventService {

    @Autowired
    private Esp32NodeMapper esp32NodeMapper;

    @Override
    public void handleStatusEvent(DeviceStatusEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("设备状态事件不能为空");
        }

        String deviceCode = cleanRequired(event.getDeviceCode(), 64, "设备状态事件缺少 deviceCode");

        Esp32Node node = esp32NodeMapper.selectByDeviceCodeIncludeDeleted(deviceCode);

        // 节点必须先通过管理接口登记，MQTT 上报不能创建节点。
        if (node == null) {
            throw new IllegalArgumentException("上报状态的 ESP32 节点尚未登记");
        }

        // 数据库使用不区分大小写的排序规则，这里仍要求 topic 使用标准编码。
        if (!deviceCode.equals(node.getDeviceCode())) {
            throw new IllegalArgumentException("MQTT topic 中的 deviceCode 与登记编码不完全一致");
        }

        if (Integer.valueOf(1).equals(node.getDelFlag())) {
            throw new IllegalArgumentException("设备已退役，拒绝状态上报");
        }

        if (event.getCurrentClients() != null && event.getCurrentClients() < 0) {
            throw new IllegalArgumentException("当前客户端数量不能为负数");
        }

        String ip = cleanNullable(event.getIp(), 45, "设备 IP");
        String firmwareVersion = cleanNullable(event.getFirmwareVersion(), 32, "固件版本");
        String wifiStatus = cleanNullable(event.getWifiStatus(), 32, "WiFi 状态");

        UpdateWrapper<Esp32Node> update = new UpdateWrapper<>();

        update.eq("node_id", node.getNodeId())
                .eq("del_flag", 0)
                .set(StringUtils.hasText(ip), "ip", ip)
                .set(StringUtils.hasText(firmwareVersion), "firmware_version", firmwareVersion)
                .set(StringUtils.hasText(wifiStatus), "wifi_status", wifiStatus)
                .set(event.getCurrentClients() != null, "current_clients", event.getCurrentClients())
                .set("status", event.getStatus() == null ? 1 : event.getStatus())
                .set("last_heartbeat", LocalDateTime.now());

        // 条件更新防止节点在读取后被并发退役。
        if (esp32NodeMapper.update(null, update) != 1) {
            throw new IllegalStateException("设备状态更新失败，节点可能已经退役");
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