package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.constant.MqttTopics;
import com.plagod.dto.DeviceCommandResult;
import com.plagod.dto.DeviceNodeVO;
import com.plagod.dto.DevicePageResult;
import com.plagod.dto.DeviceStatsVO;
import com.plagod.dto.KickDeviceDTO;
import com.plagod.dto.MacBlacklistCreateDTO;
import com.plagod.dto.MacBlacklistPageResult;
import com.plagod.dto.MacBlacklistVO;
import com.plagod.entity.Esp32Node;
import com.plagod.entity.MacBlacklist;
import com.plagod.entity.SessionRecord;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.mapper.MacBlacklistMapper;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.mqtt.MqttCommandPublisher;
import com.plagod.service.DeviceCommandService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class DeviceCommandServiceImpl implements DeviceCommandService {

    @Autowired
    private Esp32NodeMapper esp32NodeMapper;

    @Autowired
    private MacBlacklistMapper macBlacklistMapper;

    @Autowired
    private SessionRecordMapper sessionRecordMapper;

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

    @Override
    public DeviceStatsVO getDeviceStats() {
        long totalNodes = esp32NodeMapper.selectCount(new QueryWrapper<Esp32Node>());

        QueryWrapper<Esp32Node> onlineWrapper = new QueryWrapper<>();
        onlineWrapper.eq("status", 1);
        long onlineNodes = esp32NodeMapper.selectCount(onlineWrapper);
        long offlineNodes = totalNodes - onlineNodes;

        QueryWrapper<Esp32Node> clientWrapper = new QueryWrapper<>();
        clientWrapper.select("IFNULL(SUM(current_clients),0) AS current_clients");
        Esp32Node clientSummary = esp32NodeMapper.selectOne(clientWrapper);
        long currentClients = clientSummary == null || clientSummary.getCurrentClients() == null
                ? 0L
                : clientSummary.getCurrentClients().longValue();

        QueryWrapper<SessionRecord> sessionWrapper = new QueryWrapper<>();
        sessionWrapper.eq("status", 1);
        long onlineSessions = sessionRecordMapper.selectCount(sessionWrapper);

        long blacklistCount = macBlacklistMapper.selectCount(new QueryWrapper<MacBlacklist>());

        DeviceStatsVO statsVO = new DeviceStatsVO();
        statsVO.setTotalNodes(totalNodes);
        statsVO.setOnlineNodes(onlineNodes);
        statsVO.setOfflineNodes(offlineNodes);
        statsVO.setCurrentClients(currentClients);
        statsVO.setOnlineSessions(onlineSessions);
        statsVO.setBlacklistCount(blacklistCount);
        return statsVO;
    }

    @Override
    public DevicePageResult pageDevices(long current, long size, String keyword) {
        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<Esp32Node> queryWrapper = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like("device_code", keyword)
                    .or().like("name", keyword)
                    .or().like("location", keyword)
                    .or().like("ip", keyword));
        }
        queryWrapper.orderByDesc("create_time");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Esp32Node> page =
                esp32NodeMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageCurrent, pageSize), queryWrapper);

        List<DeviceNodeVO> records = new ArrayList<>();
        for (Esp32Node node : page.getRecords()) {
            DeviceNodeVO vo = new DeviceNodeVO();
            BeanUtils.copyProperties(node, vo);
            records.add(vo);
        }

        DevicePageResult result = new DevicePageResult();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }

    @Override
    public MacBlacklistPageResult pageBlacklist(long current, long size, String keyword) {
        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<MacBlacklist> queryWrapper = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like("mac", keyword)
                    .or().like("reason", keyword));
        }
        queryWrapper.orderByDesc("create_time");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<MacBlacklist> page =
                macBlacklistMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageCurrent, pageSize), queryWrapper);

        List<MacBlacklistVO> records = new ArrayList<>();
        for (MacBlacklist item : page.getRecords()) {
            MacBlacklistVO vo = new MacBlacklistVO();
            BeanUtils.copyProperties(item, vo);
            records.add(vo);
        }

        MacBlacklistPageResult result = new MacBlacklistPageResult();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }
}
