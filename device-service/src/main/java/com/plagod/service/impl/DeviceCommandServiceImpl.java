package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.audit.Audited;
import com.plagod.constant.MqttTopics;
import com.plagod.dto.device.*;
import com.plagod.vo.device.*;
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
    @Audited(action = "device.restore")
    public DeviceNodeVO restoreDevice(Long nodeId) {
        if (nodeId == null) {
            throw new IllegalArgumentException("设备号不能为空");
        }
        Esp32Node esp32Node = esp32NodeMapper.selectByNodeIdIncludeDeleted(nodeId);
        if (esp32Node == null) {
            throw new IllegalArgumentException("该退役设备不存在");
        }
        if (!Integer.valueOf(1).equals(esp32Node.getDelFlag())) {
            throw new IllegalArgumentException("设备恢复失败，该设备未退役");
        }

        int rows = esp32NodeMapper.restoreRetiredById(nodeId);

        if (rows != 1) {
            throw new IllegalArgumentException("设备恢复失败，请刷新后再试");
        }

        esp32Node = esp32NodeMapper.selectById(nodeId);
        DeviceNodeVO deviceNodeVO = new DeviceNodeVO();
        BeanUtils.copyProperties(esp32Node, deviceNodeVO);

        return deviceNodeVO;
    }

    @Override
    @Audited(action = "device.create")
    public DeviceNodeVO createDevice(DeviceNodeCreateDTO createDTO) {
        //清洗数据
        String cleanDeviceCode = createDTO.getDeviceCode().trim();
        createDTO.setName(createDTO.getName().trim());
        if (!StringUtils.hasText(createDTO.getIp())) {
            createDTO.setIp(null);
        }else {
            createDTO.setIp(createDTO.getIp().trim());
        }
        if (!StringUtils.hasText(createDTO.getLocation())){
            createDTO.setLocation(null);
        }else {
            createDTO.setLocation(createDTO.getLocation().trim());
        }
        if (!StringUtils.hasText(createDTO.getFirmwareVersion())){
            createDTO.setFirmwareVersion(null);
        }else {
            createDTO.setFirmwareVersion(createDTO.getFirmwareVersion().trim());
        }
        createDTO.setMaxClients(createDTO.getMaxClients() == null ? 4 : createDTO.getMaxClients());



        Esp32Node esp32Node = esp32NodeMapper.selectByDeviceCodeIncludeDeleted(cleanDeviceCode);

        //判断设备是否存在
        if (esp32Node != null){
            if (Integer.valueOf(0).equals(esp32Node.getDelFlag())) {
                throw new IllegalArgumentException("设备已存在！");
            }else {
                throw new IllegalArgumentException("设备已退役，请恢复后使用");
            }
        }

        //赋值
        esp32Node = new Esp32Node();
        esp32Node.setDeviceCode(cleanDeviceCode);
        esp32Node.setName(createDTO.getName());
        esp32Node.setIp(createDTO.getIp());
        esp32Node.setLocation(createDTO.getLocation());
        esp32Node.setFirmwareVersion(createDTO.getFirmwareVersion());
        esp32Node.setMaxClients(createDTO.getMaxClients());

        esp32Node.setDelFlag(0);
        esp32Node.setCurrentClients(0);
        esp32Node.setStatus(0);

        //插入进数据库
        esp32NodeMapper.insert(esp32Node);

        //转换VO传出
        DeviceNodeVO deviceNodeVO = new DeviceNodeVO();
        BeanUtils.copyProperties(esp32Node, deviceNodeVO);

        return deviceNodeVO;
    }

    @Override
    @Audited(action = "device.update")
    public DeviceNodeVO updateDevice(Long nodeId, DeviceNodeUpdateDTO updateDTO) {
        Esp32Node oldEsp32Node = esp32NodeMapper.selectById(nodeId);
        if (oldEsp32Node == null){
            throw new IllegalArgumentException("设备不存在");
        }

        if (updateDTO.getName() != null){
            updateDTO.setName(updateDTO.getName().trim());
            if (!StringUtils.hasText(updateDTO.getName())){
                throw new IllegalArgumentException("设备名不能为空");
            }
            oldEsp32Node.setName(updateDTO.getName());
        }
        if (updateDTO.getIp() != null){
            oldEsp32Node.setIp(cleanNullableText(updateDTO.getIp()));
        }
        if (updateDTO.getLocation() != null){
            oldEsp32Node.setLocation(cleanNullableText(updateDTO.getLocation()));
        }
        oldEsp32Node.setMaxClients(updateDTO.getMaxClients() == null ? oldEsp32Node.getMaxClients() : updateDTO.getMaxClients());

        esp32NodeMapper.updateById(oldEsp32Node);
        DeviceNodeVO deviceNodeVO = new DeviceNodeVO();
        BeanUtils.copyProperties(oldEsp32Node, deviceNodeVO);

        return deviceNodeVO;

    }


    @Override
    @Audited(action = "device.delete")
    public void deleteDevice(Long nodeId) {
        Esp32Node esp32Node = esp32NodeMapper.selectById(nodeId);
        Long activeSessionCount = sessionRecordMapper.selectCount(
                new QueryWrapper<SessionRecord>()
                        .eq("node_id", nodeId)
                        .eq("status",1)
                );

        if (esp32Node == null) {
            throw new IllegalArgumentException("该设备节点不存在");
        }
        if (Integer.valueOf(1).equals(esp32Node.getStatus())) {
            throw new IllegalArgumentException("当前设备在线，不能退役");
        }
        if (esp32Node.getCurrentClients() != null && esp32Node.getCurrentClients() > 0) {
            throw new IllegalArgumentException("设备存在在线客户，不能退役");
        }
        if (activeSessionCount != null && activeSessionCount > 0) {
            throw new IllegalArgumentException("设备存在活跃会话，不能退役");
        }

        int count = esp32NodeMapper.deleteById(nodeId);

        if (count == 0) {
            throw new IllegalArgumentException("设备节点删除失败");
        }

    }

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
    @Audited(action = "device.allow")
    public DeviceCommandResult allowDevice(String deviceCode) {
        String topic = MqttTopics.deviceAllow(deviceCode);
        String payload = "{\"deviceCode\":\"" + deviceCode + "\"}";
        mqttCommandPublisher.publish(topic, payload);
        return new DeviceCommandResult(topic, payload);
    }

    @Override
    @Audited(action = "device.kick")
    public DeviceCommandResult kickDevice(String deviceCode, KickDeviceDTO kickDeviceDTO) {
        String topic = MqttTopics.deviceKick(deviceCode);
        String reason = kickDeviceDTO == null || kickDeviceDTO.getReason() == null ? "" : kickDeviceDTO.getReason();
        String payload = "{\"deviceCode\":\"" + deviceCode + "\",\"reason\":\"" + reason + "\"}";
        mqttCommandPublisher.publish(topic, payload);
        return new DeviceCommandResult(topic, payload);
    }

    @Override
    @Audited(action = "blacklist.add")
    public void addBlacklist(MacBlacklistCreateDTO createDTO) {
        MacBlacklist blacklist = new MacBlacklist();
        BeanUtils.copyProperties(createDTO, blacklist);
        macBlacklistMapper.insert(blacklist);
    }

    @Override
    @Audited(action = "blacklist.remove")
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



    private String cleanNullableText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        return text.trim();
    }

}
