package com.plagod.service.impl;

import com.plagod.dto.ClientSignalEvent;
import com.plagod.entity.ClientSignalRecord;
import com.plagod.entity.Esp32Node;
import com.plagod.entity.SessionRecord;
import com.plagod.mapper.ClientSignalMapper;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.service.ClientSignalEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ClientSignalEventServiceImpl implements ClientSignalEventService {

    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");

    @Autowired
    private Esp32NodeMapper esp32NodeMapper;

    @Autowired
    private SessionRecordMapper sessionRecordMapper;

    @Autowired
    private ClientSignalMapper clientSignalMapper;

    @Override
    @Transactional
    public void handleClientSignalEvent(ClientSignalEvent event) {
        if (event == null || !StringUtils.hasText(event.getDeviceCode())) {
            throw new IllegalArgumentException("客户端信号事件缺少设备编码 deviceCode");
        }

        String deviceCode = event.getDeviceCode().trim();

        Esp32Node node = esp32NodeMapper.selectByDeviceCodeIncludeDeleted(deviceCode);

        if (node == null) {
            throw new IllegalArgumentException("RSSI 上报设备不存在:" + deviceCode);
        }

        if (Integer.valueOf(1).equals(node.getDelFlag())) {
            throw new IllegalArgumentException("RSSI 上报设备已退役:" + deviceCode);
        }

        List<ClientSignalEvent.ClientSignalItem> clients = event.getClients();

        if (clients == null || clients.isEmpty()) {
            return;
        }

        LocalDateTime reportTime = LocalDateTime.now();

        int savedCount = 0;
        int ignoredCount = 0;

        for (ClientSignalEvent.ClientSignalItem item : clients) {
            if (!isBasicDataValid(item)) {
                ignoredCount++;
                continue;
            }

            String mac = normalizeMac(item.getMac());
            long sessionId = item.getSessionId();

            if (sessionId > 0 && !isSessionRelationshipValid(sessionId, node.getNodeId(), mac)) {
                log.warn("忽略会话关系不匹配的 RSSI，device={},mac={},sessionId={}", deviceCode, mac, sessionId);
                ignoredCount++;
                continue;
            }

            ClientSignalRecord record = new ClientSignalRecord();
            record.setNodeId(node.getNodeId());
            record.setDeviceCode(deviceCode);
            record.setMac(mac);
            record.setSessionId(sessionId);
            record.setRssi(item.getRssi());
            record.setState(item.getState().trim().toUpperCase(Locale.ROOT));
            record.setReportTime(reportTime);

            clientSignalMapper.insert(record);
            savedCount++;
        }

        log.info("客户端 RSSI 批次处理完成，deviceCode={}, saved={}, ignored={}", deviceCode, savedCount, ignoredCount);
    }


    // 检查单条客户端信号记录的基础字段。
    private boolean isBasicDataValid(ClientSignalEvent.ClientSignalItem item) {
        if (item == null) {
            return false;
        }

        String mac = normalizeMac(item.getMac());

        if (mac == null) {
            log.warn("忽略非法 MAC 的 RSSI 记录，mac={}", item.getMac());
            return false;
        }

       if (item.getSessionId() == null || item.getSessionId() < 0) {
           log.warn("忽略非法 SessionId 的 RSSI 记录，mac={},sessionId={}", mac, item.getSessionId());
           return false;
       }

        // ESP32 的 RSSI 使用 int8_t, 0表示无有效测量，因此这里只接受 -127 到 -1。
        if (item.getRssi() == null || item.getRssi() < -127 || item.getRssi() >= 0) {
            log.warn("忽略非法 RSSI 记录，mac={},rssi={}", mac, item.getRssi());
            return false;
        }

        if (!StringUtils.hasText(item.getState())) {
            log.warn("忽略空客户端状态，mac={}", mac);
            return false;
        }

        String state = item.getState().trim();

        if (state.length() > 32) {
            log.warn("忽略非法客户端状态，mac={}, state={}", mac, state);
            return false;
        }
        return true;
    }

    private boolean isSessionRelationshipValid(Long sessionId, Long nodeId, String mac) {

        SessionRecord session = sessionRecordMapper.selectById(sessionId);

        if (session == null) {
            return false;
        }

        if (!Objects.equals(session.getNodeId(), nodeId)) {
            return false;
        }

        return mac.equals(normalizeMac(session.getMac()));
    }


    // 将合法MAC统一转化成大写
    private String normalizeMac(String mac) {
        if (!StringUtils.hasText(mac)) {
            return null;
        }

        String normalizedMac = mac.trim().toUpperCase(Locale.ROOT);

        if (!MAC_PATTERN.matcher(normalizedMac).matches()) {
            return null;
        }

        return normalizedMac;
    }
}