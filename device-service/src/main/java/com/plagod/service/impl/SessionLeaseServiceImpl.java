package com.plagod.service.impl;

import com.plagod.client.UserEntitlementClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.user.EntitlementLeaseRequest;
import com.plagod.entity.Esp32Node;
import com.plagod.entity.SessionRecord;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.service.DeviceCommandService;
import com.plagod.service.SessionLeaseService;
import com.plagod.vo.user.EntitlementLeaseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class SessionLeaseServiceImpl implements SessionLeaseService {

    private static final long MAX_USAGE_SECONDS = 10L;
    private static final DateTimeFormatter REQUEST_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Autowired
    private SessionRecordMapper sessionRecordMapper;
    @Autowired
    private Esp32NodeMapper esp32NodeMapper;
    @Autowired
    private UserEntitlementClient userEntitlementClient;
    @Autowired
    private DeviceCommandService deviceCommandService;

    @Value("${wifi.internal.token}")
    private String internalToken;

    @Value("${wifi.portal.lease-ttl-seconds:20}")
    private int leaseTtlSeconds;

    @Value("${wifi.portal.session-offline-timeout-seconds:30}")
    private long offlineTimeoutSeconds;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processSession(Long sessionId) {
        SessionRecord session = sessionRecordMapper.selectByIdForUpdate(sessionId);
        if (session == null || !Integer.valueOf(1).equals(session.getStatus())) {
            return;
        }

        validateConfiguration();

        LocalDateTime now = LocalDateTime.now();
        Esp32Node node = esp32NodeMapper.selectById(session.getNodeId());
        boolean nodeUnavailable = node == null || Integer.valueOf(1).equals(node.getDelFlag()) || !Integer.valueOf(1).equals(node.getStatus());

        if (session.getLastSeenTime() == null) {
            handleUnconfirmedSession(session, now, nodeUnavailable);
            return;
        }

        LocalDateTime billedTime = session.getLastBilledTime();
        if (billedTime == null) {
            billedTime = session.getLoginTime();
        }
        if (billedTime == null) {
            throw new IllegalStateException("Session 缺少计费基准时间");
        }

        // 防止异常未来时间扩大计费区间。
        LocalDateTime observedTime = session.getLastSeenTime().isAfter(now) ? now : session.getLastSeenTime();
        LocalDateTime billingCutoff = observedTime.isAfter(billedTime) ? observedTime : billedTime;

        long elapsedSeconds = Math.max(0L, Duration.between(billedTime, billingCutoff).getSeconds());
        // 单轮最多扣 10 秒；超出的历史缺口直接放弃，避免异常补扣。
        long usageSeconds = Math.min(elapsedSeconds, MAX_USAGE_SECONDS);

        boolean offline = nodeUnavailable || !observedTime.isAfter(now.minusSeconds(offlineTimeoutSeconds));
        // 已经没有待结算时长的离线 Session 可以直接关闭。
        if (offline && usageSeconds == 0) {
            closeSession(session, now, nodeUnavailable ? "NODE_UNAVAILABLE" : "RSSI_TIMEOUT");
            saveSession(session);
            return;
        }

        EntitlementLeaseResult lease = acquireLease(session, billedTime, usageSeconds);
        applyLeaseResult(session, lease, billingCutoff, usageSeconds);

        if (!Boolean.TRUE.equals(lease.getAllowed())) {
            closeSession(session, now, lease.getReason());
            saveSession(session);
            return;
        }

        Integer ttlSeconds = lease.getTtlSeconds();
        if (ttlSeconds == null || ttlSeconds < 1 || ttlSeconds > leaseTtlSeconds) {
            throw new IllegalStateException("权益服务返回了无效的续租 TTL");
        }

        // 离线时只进行最后结算，不再重新发布 ALLOW。
        if (offline) {
            closeSession(session, now, nodeUnavailable ? "NODE_UNAVAILABLE" : "RSSI_TIMEOUT");
            saveSession(session);
            return;
        }

        session.setLastRenewTime(now);
        session.setExpireTime(now.plusSeconds(ttlSeconds));
        saveSession(session);

        // Session 更新和续租命令入队共享当前事务。
        deviceCommandService.refreshClientLease(session.getNodeId(), node.getDeviceCode(), session.getMac(), session.getSessionId(), ttlSeconds);
    }

    private void validateConfiguration() {
        if (leaseTtlSeconds < 1 || leaseTtlSeconds > 20) {
            throw new IllegalStateException("续租 TTL 必须在 1 到 20 秒之间");
        }
        if (offlineTimeoutSeconds <= 0) {
            throw new IllegalStateException("Session 离线窗口必须大于 0");
        }
    }

    private void handleUnconfirmedSession(SessionRecord session, LocalDateTime now, boolean nodeUnavailable) {
        LocalDateTime anchor = session.getLastRenewTime() != null ? session.getLastRenewTime() : session.getLoginTime();

        boolean confirmationExpired = anchor == null || !now.isBefore(anchor.plusSeconds(offlineTimeoutSeconds));

        if (nodeUnavailable || confirmationExpired) {
            closeSession(session, now, nodeUnavailable ? "NODE_UNAVAILABLE" : "RSSI_NOT_CONFIRMED");
            saveSession(session);
        }
    }

    private void closeSession(SessionRecord session, LocalDateTime now, String reason) {
        String endReason = StringUtils.hasText(reason) ? reason.trim() : "ENTITLEMENT_DENIED";

        session.setStatus(0);
        session.setExpireTime(now);
        session.setLogoutTime(now);
        session.setEndReason(endReason.length() <= 32 ? endReason : endReason.substring(0, 32));
    }

    private void saveSession(SessionRecord session) {
        // Session 已经通过 for update 锁定，此处使用完整实体更新不会覆盖并发修改。
        if (sessionRecordMapper.updateById(session) != 1) {
            throw new IllegalStateException("Session 续租状态保存失败");
        }
    }

    private EntitlementLeaseResult acquireLease(SessionRecord session, LocalDateTime billedTime, Long usageSeconds) {
        EntitlementLeaseRequest request = new EntitlementLeaseRequest();
        request.setRequestId("session-lease-" + session.getSessionId() + "-" + billedTime.format(REQUEST_TIME));
        request.setUserId(session.getUserId());
        request.setSessionId(session.getSessionId());
        request.setUsageSeconds(usageSeconds);
        request.setRequestedTtlSeconds(leaseTtlSeconds);

        ApiResponse<EntitlementLeaseResult> response = userEntitlementClient.acquireLease(internalToken, request);

        if (response == null || response.getCode() != 200 || response.getData() == null) {
            throw new IllegalStateException("权益续租服务调用失败");
        }
        return response.getData();
    }

    private void applyLeaseResult(SessionRecord session, EntitlementLeaseResult lease, LocalDateTime billingCutoff, Long requestedUsageSeconds) {
        long chargedSeconds = lease.getChargedSeconds() == null ? 0L : lease.getChargedSeconds();

        if (chargedSeconds < 0 || chargedSeconds > requestedUsageSeconds) {
            throw new IllegalStateException("权益服务返回了非法扣费秒数");
        }

        if (lease.getEntitlementId() != null) {
            session.setEntitlementId(lease.getEntitlementId());
        }
        if (StringUtils.hasText(lease.getMode())) {
            session.setAuthorizationMode(lease.getMode());
        }

        // 即使历史间隔很大，也只扣最多 10 秒，然后把计费基线推进到本次观测点。
        session.setLastBilledTime(billingCutoff);

        long consumed = session.getConsumedSeconds() == null ? 0L : session.getConsumedSeconds();
        session.setConsumedSeconds(consumed + chargedSeconds);
    }
}