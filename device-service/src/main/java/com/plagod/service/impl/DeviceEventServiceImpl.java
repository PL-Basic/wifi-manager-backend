package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
        if (event == null || !StringUtils.hasText(event.getDeviceCode())) {
            throw new IllegalArgumentException("设备状态事件缺少 deviceCode");
        }

        QueryWrapper<Esp32Node> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("device_code", event.getDeviceCode());
        Esp32Node node = esp32NodeMapper.selectOne(queryWrapper);
        if (node == null) {
            node = new Esp32Node();
            node.setDeviceCode(event.getDeviceCode());
            node.setName(event.getDeviceCode());
            node.setMaxClients(4);
            node.setCurrentClients(0);
            node.setStatus(0);
            node.setDelFlag(0);
        }

        if (StringUtils.hasText(event.getIp())) {
            node.setIp(event.getIp());
        }
        if (StringUtils.hasText(event.getFirmwareVersion())) {
            node.setFirmwareVersion(event.getFirmwareVersion());
        }
        if (event.getCurrentClients() != null) {
            node.setCurrentClients(event.getCurrentClients());
        }
        node.setStatus(event.getStatus() == null ? 1 : event.getStatus());
        node.setLastHeartbeat(LocalDateTime.now());

        if (node.getNodeId() == null) {
            esp32NodeMapper.insert(node);
        } else {
            esp32NodeMapper.updateById(node);
        }
    }
}
