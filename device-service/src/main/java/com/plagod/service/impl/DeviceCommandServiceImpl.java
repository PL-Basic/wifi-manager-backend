package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.constant.MqttTopics;
import com.plagod.dto.DeviceCommandResult;
import com.plagod.dto.DeviceNodeVO;
import com.plagod.dto.KickDeviceDTO;
import com.plagod.dto.MacBlacklistCreateDTO;
import com.plagod.entity.Esp32Node;
import com.plagod.entity.MacBlacklist;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.mapper.MacBlacklistMapper;
import com.plagod.mqtt.MqttCommandPublisher;
import com.plagod.service.DeviceCommandService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DeviceCommandServiceImpl implements DeviceCommandService {

    @Autowired
    private Esp32NodeMapper esp32NodeMapper;

    @Autowired
    private MacBlacklistMapper macBlacklistMapper;

    @Autowired
    private MqttCommandPublisher mqttCommandPublisher;

    @Override
    public DeviceNodeVO getDevice(Long nodeId) {
        Esp32Node esp32Node = esp32NodeMapper.selectById(nodeId);
        if (esp32Node == null) {
            throw new IllegalArgumentException("节点不存在");
        }
        DeviceNodeVO vo = new DeviceNodeVO();
        BeanUtils.copyProperties(esp32Node, vo);
        return vo;
    }

    @Override
    public DeviceCommandResult allowDevice(String deviceCode) {
        String topic = MqttTopics.deviceAllow(deviceCode);
        String payload = "{\"deviceCode\":\"" + deviceCode + "\"}";
        mqttCommandPublisher.publish(topic, payload);
        return new DeviceCommandResult(topic, payload);
    }

    @Override
    public DeviceCommandResult kickDevice(String deviceCode, KickDeviceDTO kickDeviceDTO) {
        String topic = MqttTopics.deviceKick(deviceCode);
        String reason = kickDeviceDTO == null || kickDeviceDTO.getReason() == null ? "" : kickDeviceDTO.getReason();
        String payload = "{\"deviceCode\":\"" + deviceCode + "\",\"reason\":\"" + reason + "\"}";
        mqttCommandPublisher.publish(topic, payload);
        return new DeviceCommandResult(topic, payload);
    }

    @Override
    public void addBlacklist(MacBlacklistCreateDTO createDTO) {
        MacBlacklist blacklist = new MacBlacklist();
        BeanUtils.copyProperties(createDTO, blacklist);
        macBlacklistMapper.insert(blacklist);
    }

    @Override
    public void removeBlacklist(String mac) {
        QueryWrapper<MacBlacklist> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("mac", mac);
        int count = macBlacklistMapper.delete(queryWrapper);
        if (count == 0) {
            throw new IllegalArgumentException("黑名单记录不存在");
        }
    }
}
