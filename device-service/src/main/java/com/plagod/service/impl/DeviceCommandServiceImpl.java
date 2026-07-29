package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.audit.Audited;
import com.plagod.constant.DeviceCommandPurpose;
import com.plagod.constant.MqttTopics;
import com.plagod.constant.SessionStatus;
import com.plagod.dto.AllowClientCommand;
import com.plagod.dto.RevokeAccessCommand;
import com.plagod.dto.device.*;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.service.DeviceCommandOutboxService;
import com.plagod.service.ManagedDeviceCommandService;
import com.plagod.vo.device.*;
import com.plagod.entity.device.Esp32Node;
import com.plagod.entity.device.MacBlacklist;
import com.plagod.entity.device.SessionRecord;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.mapper.MacBlacklistMapper;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.service.DeviceCommandService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DeviceCommandServiceImpl implements DeviceCommandService {

    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");


    @Autowired
    private Esp32NodeMapper esp32NodeMapper;

    @Autowired
    private MacBlacklistMapper macBlacklistMapper;

    @Autowired
    private SessionRecordMapper sessionRecordMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceCommandOutboxService commandOutboxService;

    @Autowired
    private ManagedDeviceCommandService managedDeviceCommandService;


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
        Long openSessionCount = sessionRecordMapper.selectCount(
                new QueryWrapper<SessionRecord>()
                        .eq("node_id", nodeId)
                        .in("status", SessionStatus.ACTIVE, SessionStatus.PENDING, SessionStatus.WAITING_REPLACEMENT)
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
        if (openSessionCount != null && openSessionCount > 0) {
            throw new IllegalArgumentException("设备存在尚未关闭的会话，不能退役");
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
    @Transactional(rollbackFor = Exception.class)
    public DeviceNodeVO allowDevice(String deviceCode) {
        if (!StringUtils.hasText(deviceCode)) {
            throw new IllegalArgumentException("deviceCode 不能为空");
        }

        String cleanedDeviceCode = deviceCode.trim();

        if (cleanedDeviceCode.length() > 64) {
            throw new IllegalArgumentException("deviceCode 长度超限");
        }

        Esp32Node node = esp32NodeMapper.selectByDeviceCodeIncludeDeleted(cleanedDeviceCode);

        // 节点授权不能被 MQTT 心跳反向创建。
        if (node == null) {
            throw new IllegalArgumentException("ESP32 节点尚未登记，请先创建设备节点");
        }

        if (!cleanedDeviceCode.equals(node.getDeviceCode())) {
            throw new IllegalArgumentException("deviceCode 与节点登记编码不完全一致");
        }

        if (Integer.valueOf(1).equals(node.getDelFlag())) {
            int restored = esp32NodeMapper.restoreRetiredById(node.getNodeId());
            if (restored != 1) {
                // 并发授权时，另一请求可能已经完成恢复。
                Esp32Node concurrentResult = esp32NodeMapper.selectByNodeIdIncludeDeleted(node.getNodeId());

                if (concurrentResult == null || Integer.valueOf(1).equals(concurrentResult.getDelFlag())) {
                    throw new IllegalStateException("ESP32 节点授权失败，请刷新后重试");
                }
                node = concurrentResult;
            } else {
                node = esp32NodeMapper.selectByNodeIdIncludeDeleted(node.getNodeId());
            }
        }

        if (node == null || Integer.valueOf(1).equals(node.getDelFlag())) {
            throw new IllegalStateException("ESP32 节点授权结果异常");
        }

        DeviceNodeVO result = new DeviceNodeVO();
        BeanUtils.copyProperties(node, result);
        return result;
    }

    @Override
    @Audited(action = "device.kick")
    public DeviceCommandResult kickDevice(String deviceCode, KickDeviceDTO kickDeviceDTO) {
        String reason = kickDeviceDTO == null ? null : kickDeviceDTO.getReason();

        return managedDeviceCommandService.enqueueKick(deviceCode, reason, DeviceCommandPurpose.MANUAL_DEVICE_RESTART);
    }
    @Override
    @Audited(action = "device.allow-client")
    public DeviceCommandResult allowClient(Long nodeId, String deviceCode, String mac, Long sessionId, Integer ttlSeconds) {
        return enqueueClientLease(nodeId, deviceCode, mac, sessionId, ttlSeconds, DeviceCommandPurpose.PORTAL_AUTHORIZE);
    }

    @Override
    public DeviceCommandResult refreshClientLease(Long nodeId, String deviceCode, String mac, Long sessionId, Integer ttlSeconds) {
        return enqueueClientLease(nodeId, deviceCode, mac, sessionId, ttlSeconds, DeviceCommandPurpose.LEASE_RENEW);
    }

    @Override
    public DeviceCommandResult revokeClientAccess(Long nodeId, String deviceCode, String mac, Long sessionId, String purpose) {
        if (nodeId == null || nodeId <= 0) {
            throw new IllegalArgumentException("nodeId 必须是有效值");
        }
        if (!StringUtils.hasText(deviceCode)) {
            throw new IllegalArgumentException("deviceCode 不能为空");
        }

        String normalizedDeviceCode = deviceCode.trim();
        String normalizedMac = normalizeMac(mac);

        if (normalizedMac == null) {
            throw new IllegalArgumentException("客户端 MAC 格式不正确");
        }
        if (sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException("sessionId 必须是有效值");
        }
        if (!DeviceCommandPurpose.isSessionRevokePurpose(purpose)) {
            throw new IllegalArgumentException("Session 撤销命令用途无效");
        }

        String requestId = UUID.randomUUID().toString();
        String topic = MqttTopics.deviceRevokeAccess(normalizedDeviceCode);
        RevokeAccessCommand body = new RevokeAccessCommand(requestId, normalizedMac, sessionId);

        try {
            String payload = objectMapper.writeValueAsString(body);

            DeviceCommandRecord command = new DeviceCommandRecord();
            command.setRequestId(requestId);
            command.setNodeId(nodeId);
            command.setDeviceCode(normalizedDeviceCode);
            command.setCommandType("REVOKE_ACCESS");
            command.setPurpose(purpose);
            command.setSessionId(sessionId);
            command.setMac(normalizedMac);
            command.setTopic(topic);
            command.setPayload(payload);

            commandOutboxService.enqueue(command);
            return new DeviceCommandResult(requestId, topic, payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("REVOKE_ACCESS 命令序列化失败", exception);
        }
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
        sessionWrapper.eq("status", SessionStatus.ACTIVE);
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

    private String normalizeMac(String mac) {
        if (!StringUtils.hasText(mac)) {
            return null;
        }
        String normalized = mac.trim().toUpperCase(Locale.ROOT);
        return MAC_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    // Portal 首次授权和后台续租共用同一套参数校验与 MQTT 序列化逻辑。
    private DeviceCommandResult enqueueClientLease(Long nodeId, String deviceCode, String mac, Long sessionId, Integer ttlSeconds, String purpose) {

        if (nodeId == null || nodeId <= 0) {
            throw new IllegalArgumentException("nodeId 必须是有效值");
        }
        if (!StringUtils.hasText(deviceCode)) {
            throw new IllegalArgumentException("deviceCode 不能为空");
        }

        String normalizedDeviceCode = deviceCode.trim();
        String normalizedMac = normalizeMac(mac);

        if (normalizedMac == null) {
            throw new IllegalArgumentException("客户端 MAC 格式不正确");
        }
        if (sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException("sessionId 必须是有效值");
        }
        if (ttlSeconds == null || ttlSeconds < 1 || ttlSeconds > 86400) {
            throw new IllegalArgumentException("ttlSeconds 必须在 1 到 86400 之间");
        }

        String requestId = UUID.randomUUID().toString();
        String topic = MqttTopics.deviceAllow(normalizedDeviceCode);
        AllowClientCommand body = new AllowClientCommand(requestId, normalizedMac, sessionId, ttlSeconds);

        try {
            String payload = objectMapper.writeValueAsString(body);

            DeviceCommandRecord command = new DeviceCommandRecord();
            command.setRequestId(requestId);
            command.setNodeId(nodeId);
            command.setDeviceCode(normalizedDeviceCode);
            command.setCommandType("ALLOW");
            command.setPurpose(purpose);
            command.setSessionId(sessionId);
            command.setMac(normalizedMac);
            command.setTtlSeconds(ttlSeconds);
            command.setTopic(topic);
            command.setPayload(payload);

            commandOutboxService.enqueue(command);
            return new DeviceCommandResult(requestId, topic, payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("ALLOW 命令序列化失败", exception);
        }
    }
}