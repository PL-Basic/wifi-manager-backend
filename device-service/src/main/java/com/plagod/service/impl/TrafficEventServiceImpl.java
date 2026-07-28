package com.plagod.service.impl;

import com.plagod.constant.SessionStatus;
import com.plagod.dto.DeviceTrafficEvent;
import com.plagod.entity.Esp32Node;
import com.plagod.entity.SessionRecord;
import com.plagod.entity.TrafficLog;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.mapper.TrafficLogMapper;
import com.plagod.service.TrafficEventService;
import com.plagod.service.TrafficRuleEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TrafficEventServiceImpl implements TrafficEventService {

    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");

    @Autowired
    private TrafficLogMapper trafficLogMapper;

    @Autowired
    private SessionRecordMapper sessionRecordMapper;

    @Autowired
    private Esp32NodeMapper esp32NodeMapper;

    @Autowired
    private TrafficRuleEvaluator trafficRuleEvaluator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleTrafficEvent(DeviceTrafficEvent event) {
        normalizeAndValidate(event);

        Esp32Node node = esp32NodeMapper.selectByDeviceCodeIncludeDeleted(event.getDeviceCode());

        if (node == null || Integer.valueOf(1).equals(node.getDelFlag())) {
            throw new IllegalArgumentException("流量事件对应的 ESP32 节点不存在或已退役");
        }

        if (!event.getDeviceCode().equals(node.getDeviceCode())) {
            throw new IllegalArgumentException("流量事件 deviceCode 与节点登记编码不完全一致");
        }

        SessionRecord session = sessionRecordMapper.selectById(event.getSessionId());

        validateSessionRelation(event, node, session);

        TrafficLog trafficLog = buildTrafficLog(event, node);

        int inserted = trafficLogMapper.insertIgnore(trafficLog);

        if (inserted == 0) {
            handleDuplicateEvent(trafficLog);
            return;
        }

        int updated = sessionRecordMapper.incrementTrafficIfActive(
                session.getSessionId(),
                node.getNodeId(),
                event.getMac(),
                SessionStatus.ACTIVE,
                event.getBytesUp(),
                event.getBytesDown());

        // Session 在前置校验后被关闭或关系改变时，流量插入也必须回滚。
        if (updated != 1) {
            throw new IllegalStateException("流量累计失败，Session 状态或关联关系已经变化");
        }

        registerRuleEvaluationAfterCommit(event, session);

        log.info("流量事件保存成功，deviceCode={}, eventId={}, sessionId={}", event.getDeviceCode(), event.getEventId(), event.getSessionId());
    }

    private void normalizeAndValidate(DeviceTrafficEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("流量事件不能为空");
        }

        event.setEventId(cleanRequired(event.getEventId(), 64, "流量事件缺少 eventId"));
        event.setDeviceCode(cleanRequired(event.getDeviceCode(), 64, "流量事件缺少 deviceCode"));
        event.setDstIp(cleanRequired(event.getDstIp(), 45, "流量事件缺少目标 IP"));
        event.setSni(cleanNullable(event.getSni(), 255, "SNI"));
        event.setProtocol(cleanNullable(event.getProtocol(), 16, "协议"));

        String normalizedMac = normalizeMac(event.getMac());
        if (normalizedMac == null) {
            throw new IllegalArgumentException("流量事件 MAC 格式不正确");
        }
        event.setMac(normalizedMac);

        if (event.getSessionId() == null || event.getSessionId() <= 0) {
            throw new IllegalArgumentException("流量事件必须提供有效 sessionId");
        }

        if (event.getDstPort() != null && (event.getDstPort() < 1 || event.getDstPort() > 65535)) {
            throw new IllegalArgumentException("目标端口必须在 1 到 65535 之间");
        }

        long bytesUp = event.getBytesUp() == null ? 0L : event.getBytesUp();
        long bytesDown = event.getBytesDown() == null ? 0L : event.getBytesDown();

        if (bytesUp < 0 || bytesDown < 0) {
            throw new IllegalArgumentException("流量字节数不能为负数");
        }

        event.setBytesUp(bytesUp);
        event.setBytesDown(bytesDown);
    }

    private void validateSessionRelation(DeviceTrafficEvent event, Esp32Node node, SessionRecord session) {
        if (session == null) {
            throw new IllegalArgumentException("流量事件 Session 不存在");
        }

        if (!Integer.valueOf(SessionStatus.ACTIVE).equals(session.getStatus())) {
            throw new IllegalArgumentException("流量事件只能关联 ACTIVE Session");
        }

        if (!Objects.equals(node.getNodeId(), session.getNodeId())) {
            throw new IllegalArgumentException("流量事件节点与 Session 节点不一致");
        }

        String sessionMac = normalizeMac(session.getMac());

        if (!event.getMac().equals(sessionMac)) {
            throw new IllegalArgumentException("流量事件 MAC 与 Session MAC 不一致");
        }
    }

    private TrafficLog buildTrafficLog(DeviceTrafficEvent event,
                                       Esp32Node node) {
        TrafficLog trafficLog = new TrafficLog();

        trafficLog.setEventId(event.getEventId());
        trafficLog.setNodeId(node.getNodeId());
        trafficLog.setDeviceCode(node.getDeviceCode());
        trafficLog.setSessionId(event.getSessionId());
        trafficLog.setMac(event.getMac());
        trafficLog.setDstIp(event.getDstIp());
        trafficLog.setDstPort(event.getDstPort());
        trafficLog.setSni(event.getSni());
        trafficLog.setProtocol(event.getProtocol());
        trafficLog.setBytesUp(event.getBytesUp());
        trafficLog.setBytesDown(event.getBytesDown());
        trafficLog.setLogTime(LocalDateTime.now());

        return trafficLog;
    }

    private void handleDuplicateEvent(TrafficLog candidate) {
        TrafficLog existing = trafficLogMapper.selectByEventIdentityForUpdate(candidate.getDeviceCode(), candidate.getEventId());

        if (existing == null) {
            throw new IllegalStateException("流量事件唯一键冲突，但未找到已有记录");
        }

        if (!sameLogicalEvent(existing, candidate)) {
            throw new IllegalArgumentException("相同 eventId 已被不同流量事件占用");
        }

        log.info("忽略重复流量事件，deviceCode={}, eventId={}", candidate.getDeviceCode(), candidate.getEventId());
    }

    private boolean sameLogicalEvent(TrafficLog first, TrafficLog second) {
        return Objects.equals(first.getNodeId(), second.getNodeId())
                && Objects.equals(first.getSessionId(), second.getSessionId())
                && Objects.equals(first.getMac(), second.getMac())
                && Objects.equals(first.getDstIp(), second.getDstIp())
                && Objects.equals(first.getDstPort(), second.getDstPort())
                && Objects.equals(first.getSni(), second.getSni())
                && Objects.equals(first.getProtocol(), second.getProtocol())
                && Objects.equals(first.getBytesUp(), second.getBytesUp())
                && Objects.equals(first.getBytesDown(), second.getBytesDown());
    }

    private void registerRuleEvaluationAfterCommit(DeviceTrafficEvent event, SessionRecord session) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCommit() {
                        try {
                            trafficRuleEvaluator.evaluateAndAct(event, session.getSessionId(), session);
                        } catch (Exception exception) {
                            // 数据已经提交，规则旁路失败不能反向影响流量主链路。
                            log.warn("提交后规则评估调度失败，eventId={}", event.getEventId(), exception);
                        }
                    }
                }
        );
    }

    private String normalizeMac(String mac) {
        if (!StringUtils.hasText(mac)) {
            return null;
        }

        String normalized = mac.trim().toUpperCase(Locale.ROOT);

        return MAC_PATTERN.matcher(normalized).matches() ? normalized : null;
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