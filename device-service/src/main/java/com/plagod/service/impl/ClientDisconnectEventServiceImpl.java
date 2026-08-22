package com.plagod.service.impl;

import com.plagod.constant.SessionStatus;
import com.plagod.dto.ClientDisconnectEvent;
import com.plagod.entity.device.Esp32Node;
import com.plagod.entity.device.SessionRecord;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.service.ClientDisconnectEventService;
import com.plagod.service.SessionLeaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ClientDisconnectEventServiceImpl implements ClientDisconnectEventService {

    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");

    private static final String AUTHORIZED_STATE = "AUTHORIZED";
    private static final String END_REASON = "CLIENT_DISCONNECT";

    @Autowired
    private SessionRecordMapper sessionRecordMapper;

    @Autowired
    private Esp32NodeMapper esp32NodeMapper;

    @Autowired
    private SessionLeaseService sessionLeaseService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Override
    @Transactional(
            propagation = Propagation.NOT_SUPPORTED,
            rollbackFor = Exception.class)
    public void handleClientDisconnectEvent(ClientDisconnectEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("客户端断线事件不能为空");
        }

        String deviceCode = cleanDeviceCode(event.getDeviceCode());
        Esp32Node topicNode = esp32NodeMapper.selectByDeviceCodeIncludeDeleted(deviceCode);
        if (topicNode == null || !deviceCode.equals(topicNode.getDeviceCode())) {
            throw new IllegalArgumentException("断线事件目标设备不存在");
        }
        Long sessionId = event.getSessionId();

        if (sessionId == null || sessionId < 0) {
            throw new IllegalArgumentException("客户端断线事件的 sessionId 无效");
        }

        // 未认证客户端没有后端 Session，不能反向创建。
        if (sessionId == 0) {
            log.info("忽略未认证客户端断线事件，deviceCode={}", deviceCode);
            return;
        }

        String mac = normalizeMac(event.getMac());
        if (mac == null) {
            throw new IllegalArgumentException("客户端断线事件的 MAC 无效");
        }

        String state = normalizeState(event.getState());

        // 固件约定 sessionId > 0 时客户端断开前必须处于 AUTHORIZED。
        if (!AUTHORIZED_STATE.equals(state)) {
            throw new IllegalArgumentException("携带有效 sessionId 的断线客户端不是 AUTHORIZED 状态");
        }

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        DisconnectPlan plan = transaction.execute(status ->
                prepareDisconnect(
                        topicNode.getTenantId(),
                        sessionId,
                        deviceCode,
                        mac));
        if (plan == null) {
            return;
        }

        // 准备事务已释放 Session 行锁，最终结算的 User 调用位于事务外。
        if (plan.requiresFinalSettlement) {
            sessionLeaseService.settleFinalUsage(sessionId);
        }

        transaction.executeWithoutResult(status ->
                completeDisconnect(
                        topicNode.getTenantId(),
                        sessionId,
                        deviceCode,
                        mac));

        // 客户端已经物理断线，因此这里不能再创建 REVOKE_ACCESS 命令。
        log.info("客户端断线 Session 已关闭，deviceCode={}, sessionId={}", deviceCode, sessionId);
    }

    private DisconnectPlan prepareDisconnect(
            Long tenantId,
            Long sessionId,
            String deviceCode,
            String mac) {
        SessionRecord session =
                sessionRecordMapper.selectByIdForUpdate(
                        tenantId, sessionId);
        // 未知 Session 只忽略，绝不根据固件数据反向创建。
        if (session == null) {
            log.warn(
                    "忽略未知 Session 的客户端断线事件，sessionId={}",
                    sessionId);
            return null;
        }
        // QoS 1 可能重复投递，已关闭 Session 不重复结算。
        if (!SessionStatus.isOpen(session.getStatus())) {
            log.info(
                    "忽略已关闭 Session 的重复断线事件，sessionId={}",
                    sessionId);
            return null;
        }
        validateRelationship(session, deviceCode, mac);
        return new DisconnectPlan(
                SessionStatus.isActive(session.getStatus()));
    }

    private void completeDisconnect(
            Long tenantId,
            Long sessionId,
            String deviceCode,
            String mac) {
        SessionRecord session =
                sessionRecordMapper.selectByIdForUpdate(
                        tenantId, sessionId);
        if (session == null || !SessionStatus.isOpen(session.getStatus())) {
            return;
        }
        validateRelationship(session, deviceCode, mac);
        LocalDateTime now = LocalDateTime.now();
        session.setStatus(SessionStatus.CLOSED);
        session.setExpireTime(now);
        session.setLogoutTime(now);
        session.setEndReason(END_REASON);
        if (sessionRecordMapper.updateById(session) != 1) {
            throw new IllegalStateException(
                    "客户端断线后的 Session 保存失败");
        }
    }

    private void validateRelationship(SessionRecord session, String deviceCode, String eventMac) {

        if (session.getNodeId() == null) {
            throw new IllegalStateException("Session 缺少关联节点");
        }

        Esp32Node node = esp32NodeMapper.selectByNodeIdAndTenantIncludeDeleted(
                session.getTenantId(), session.getNodeId());

        // 即使节点已经退役，也允许处理它此前产生的断线事件。
        if (node == null || !StringUtils.hasText(node.getDeviceCode())) {
            throw new IllegalStateException("Session 关联的 ESP32 节点不存在");
        }

        if (!deviceCode.equals(node.getDeviceCode().trim())) {
            throw new IllegalArgumentException("断线事件设备与 Session 关联节点不一致");
        }

        String sessionMac = normalizeMac(session.getMac());
        if (!eventMac.equals(sessionMac)) {
            throw new IllegalArgumentException("断线事件 MAC 与 Session MAC 不一致");
        }
    }

    private String cleanDeviceCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("客户端断线事件缺少 deviceCode");
        }

        String cleaned = value.trim();
        if (cleaned.length() > 64) {
            throw new IllegalArgumentException("deviceCode 长度超过限制");
        }
        return cleaned;
    }

    private String normalizeMac(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return MAC_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private String normalizeState(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("客户端断线事件缺少 state");
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 32) {
            throw new IllegalArgumentException("客户端状态长度超过限制");
        }
        return normalized;
    }

    private static final class DisconnectPlan {
        private final boolean requiresFinalSettlement;

        private DisconnectPlan(boolean requiresFinalSettlement) {
            this.requiresFinalSettlement = requiresFinalSettlement;
        }
    }
}
