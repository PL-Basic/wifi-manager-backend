package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.plagod.audit.Audited;
import com.plagod.client.UserEntitlementClient;
import com.plagod.client.UserPolicyClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.device.PortalAuthorizeDTO;
import com.plagod.dto.user.EntitlementLeaseRequest;
import com.plagod.entity.Esp32Node;
import com.plagod.entity.MacBlacklist;
import com.plagod.entity.SessionRecord;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.mapper.MacBlacklistMapper;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.mapper.SessionUserGuardMapper;
import com.plagod.service.ClientSignalQueryService;
import com.plagod.service.DeviceCommandService;
import com.plagod.service.PortalSessionService;
import com.plagod.vo.device.SessionRecordVO;
import com.plagod.vo.user.EntitlementLeaseResult;
import com.plagod.vo.user.UserConnectionPolicyVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class PortalSessionServiceImpl implements PortalSessionService {

    // 首次授权只申请短 TTL，后续由续租任务定期刷新
    private static final int INITIAL_LEASE_TTL_SECONDS = 20;
    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");

    @Autowired
    private Esp32NodeMapper esp32NodeMapper;
    @Autowired
    private MacBlacklistMapper macBlacklistMapper;
    @Autowired
    private SessionRecordMapper sessionRecordMapper;
    @Autowired
    private DeviceCommandService deviceCommandService;
    @Autowired
    private UserEntitlementClient userEntitlementClient;
    @Autowired
    private ClientSignalQueryService clientSignalQueryService;
    @Autowired
    private UserPolicyClient userPolicyClient;
    @Autowired
    private SessionUserGuardMapper sessionUserGuardMapper;

    @Value("${wifi.internal.token}")
    private String internalToken;

    // RSSI 记录允许的最大年龄。默认30秒，避免使用历史记录冒充当前在线客户端。
    @Value("${wifi.portal.client-signal-max-age-seconds:30}")
    private long clientSignalMaxAgeSeconds;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Audited(action = "session.portal-authorize")
    public SessionRecordVO authorize(PortalAuthorizeDTO dto, Long userId) {
        if (dto == null || userId == null || userId <= 0) {
            throw new IllegalArgumentException("Portal 授权参数或者用户身份无效");
        }

        String deviceCode = cleanRequired(dto.getDeviceCode(), "设备编码 deviceCode 不能为空");
        String ip = cleanRequired(dto.getIp(), "客户端 IP 不能为空");
        String mac = normalizeMac(dto.getMac());
        if (mac == null) {
            throw new IllegalArgumentException("客户端 MAC 格式不正确");
        }

        // 锁住节点行，使同一 ESP32 上的 Portal 授权请求串行执行。
        Esp32Node node = esp32NodeMapper.selectByDeviceCodeForUpdateIncludeDeleted(deviceCode);
        if (node == null || Integer.valueOf(1).equals(node.getDelFlag())) {
            throw new IllegalArgumentException("Portal 所连接的设备不存在或已退役");
        }
        if (!Integer.valueOf(1).equals(node.getStatus())) {
            throw new IllegalArgumentException("Portal 所连接设备当前不在线");
        }

        LocalDateTime now = LocalDateTime.now();

        // 永久黑名单或者尚未过期的临时黑名单都会阻止认证
        QueryWrapper<MacBlacklist> blackListQuery = new QueryWrapper<>();
        blackListQuery.eq("mac", mac)
                .and(wrapper -> wrapper.isNull("expire_time")
                        .or().gt("expire_time", now));
        if (macBlacklistMapper.selectCount(blackListQuery) > 0) {
            throw new IllegalArgumentException("该客户端已被加入黑名单");
        }

        validateRecentClientSignal(node, deviceCode, mac, now);

        SessionRecord reusableSession = findReusableActiveSession(userId, node.getNodeId(), mac);

        if (reusableSession != null) {
            // 重复认证只重新检查权益并刷新固件 TTL，不创建新 Session。
            EntitlementLeaseResult lease =
                    acquireInitialLease(userId, reusableSession.getSessionId());
            validateLease(lease);

            // 清理同一 MAC 意外残留的其他活跃记录，保留本次复用的 Session。
            closeConflictingSessions(mac, reusableSession.getSessionId(), now);

            reusableSession.setIp(ip);

            String deviceInfo = cleanNullable(dto.getDeviceInfo());
            if (deviceInfo != null) {
                reusableSession.setDeviceInfo(deviceInfo);
            }

            applyLease(reusableSession, lease, now);
            return saveAndPublish(deviceCode, reusableSession, lease.getTtlSeconds());
        }

        // 获取 user-service 统一解释后的有效连接上限。
        // 远程调用放在加锁前，避免持有数据库锁时等待网络请求。
        UserConnectionPolicyVO connectionPolicy = loadConnectionPolicy(userId);

        // 串行化同一用户的“统计名额并创建 Session”流程。
        lockSessionAllocation(userId);

        // 当前 MAC 如果正在其他节点使用，随后会替换旧 Session，
        // 因此只统计其他 MAC 占用的名额。
        validateConnectionLimit(userId, mac, connectionPolicy.getMaxConnections());

        // 没有可复用 Session，才关闭旧连接并创建新记录。
        closeConflictingSessions(mac, null, now);

        SessionRecord sessionRecord = new SessionRecord();
        sessionRecord.setUserId(userId);
        sessionRecord.setNodeId(node.getNodeId());
        sessionRecord.setMac(mac);
        sessionRecord.setIp(ip);
        sessionRecord.setDeviceInfo(cleanNullable(dto.getDeviceInfo()));
        sessionRecord.setLoginTime(now);
        sessionRecord.setExpireTime(now);
        sessionRecord.setStatus(0);
        sessionRecord.setBytesUp(0L);
        sessionRecord.setBytesDown(0L);
        sessionRecord.setConsumedSeconds(0L);

        if (sessionRecordMapper.insert(sessionRecord) != 1 || sessionRecord.getSessionId() == null) {
            throw new IllegalStateException("Portal 会话创建失败");
        }

        EntitlementLeaseResult lease = acquireInitialLease(userId, sessionRecord.getSessionId());
        validateLease(lease);

        applyLease(sessionRecord, lease, now);
        return saveAndPublish(deviceCode, sessionRecord, lease.getTtlSeconds());
    }

    private String cleanRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeMac(String mac) {
        if (!StringUtils.hasText(mac)) {
            return null;
        }
        String normalized = mac.trim().toUpperCase(Locale.ROOT);
        return MAC_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private String cleanNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    // 申请 Portal 首次授权租约。
    private EntitlementLeaseResult acquireInitialLease(Long userId, Long sessionId) {
        EntitlementLeaseRequest request = new EntitlementLeaseRequest();

        request.setRequestId("portal-init-" + sessionId);
        request.setUserId(userId);
        request.setSessionId(sessionId);
        request.setUsageSeconds(0L);
        request.setRequestedTtlSeconds(INITIAL_LEASE_TTL_SECONDS);

        ApiResponse<EntitlementLeaseResult> response = userEntitlementClient.acquireLease(internalToken, request);

        if (response == null) {
            throw new IllegalStateException("权益服务没有返回结果");
        }
        if (response.getCode() != 200) {
            throw new IllegalStateException("权益服务调度失败：" + response.getMessage());
        }
        if (response.getData() == null) {
            throw new IllegalStateException("权益服务返回的租约数据为空");
        }

        return response.getData();
    }

    // 查询可以被当前请求复用的活跃 Session。
    // for update 防止续租任务同时修改同一条 Session。
    private SessionRecord findReusableActiveSession(Long userId, Long nodeId, String mac) {
        QueryWrapper<SessionRecord> query = new QueryWrapper<>();
        query.eq("user_id", userId)
                .eq("node_id", nodeId)
                .eq("mac", mac)
                .eq("status", 1)
                .orderByDesc("session_id")
                .last("limit 1 for update");

        return sessionRecordMapper.selectOne(query);
    }

    // 结束同一 MAC 的其他活跃 Session。
    // keepSessionId 不为空时保留正在复用的 Session。
    private void closeConflictingSessions(String mac, Long keepSessionId, LocalDateTime now) {
        UpdateWrapper<SessionRecord> update = new UpdateWrapper<>();
        update.eq("mac", mac)
                .eq("status", 1);

        if (keepSessionId != null) {
            update.ne("session_id", keepSessionId);
        }

        update.set("status", 0)
                .set("logout_time", now)
                .set("end_reason", "PORTAL_REPLACED");

        sessionRecordMapper.update(null, update);
    }

    // 检查 user-service 返回的权益租约能否下发给固件。
    private void validateLease(EntitlementLeaseResult lease) {
        if (lease == null) {
            throw new IllegalStateException("权益租约不能为空");
        }

        if (!Boolean.TRUE.equals(lease.getAllowed())) {
            throw new IllegalArgumentException("当前网络权益不可用：" + lease.getReason());
        }

        Integer ttlSeconds = lease.getTtlSeconds();
        if (ttlSeconds == null || ttlSeconds < 1 || ttlSeconds > INITIAL_LEASE_TTL_SECONDS) {
            throw new IllegalStateException("权益服务返回了无效的 TTL");
        }
    }

    // 将新的权益租约应用到新 Session 或复用 Session。
    private void applyLease(SessionRecord session, EntitlementLeaseResult lease, LocalDateTime now) {

        session.setEntitlementId(lease.getEntitlementId());
        session.setAuthorizationMode(lease.getMode());
        session.setExpireTime(now.plusSeconds(lease.getTtlSeconds()));
        session.setLastRenewTime(now);
        session.setStatus(1);
        session.setLogoutTime(null);
        session.setEndReason(null);

        // 重复认证不能重置该字段，否则会漏掉尚未结算的在线时长。
        if (session.getLastBilledTime() == null) {
            session.setLastBilledTime(now);
        }
    }

    // 保存 Session，然后使用同一个 sessionId 刷新 ESP32 的短 TTL。
    private SessionRecordVO saveAndPublish(String deviceCode, SessionRecord session, Integer ttlSeconds) {
        if (sessionRecordMapper.updateById(session) != 1) {
            throw new IllegalStateException("Portal 会话状态更新失败");
        }

        deviceCommandService.allowClient(deviceCode, session.getMac(), session.getSessionId(), ttlSeconds);
        SessionRecordVO result = new SessionRecordVO();
        BeanUtils.copyProperties(session, result);
        return result;
    }

    // 校验该 MAC 最近是否被当前 ESP32 节点实际观察到
    private void validateRecentClientSignal(Esp32Node node, String deviceCode, String mac, LocalDateTime now) {
        if (clientSignalMaxAgeSeconds <= 0) {
            throw new IllegalStateException("Portal RSSI 时间窗口配置必须大于 0");
        }
        // 只接受当前时间窗口内由后端记录的 RSSI。
        LocalDateTime sinceTime = now.minusSeconds(clientSignalMaxAgeSeconds);

        boolean observed = clientSignalQueryService.wasRecentlyObserved(node.getNodeId(), deviceCode, mac, sinceTime);
        if (!observed) {
            throw new IllegalArgumentException("当前 ESP32 最近未观察到该客户端，请确认仍连接热点后重试");
        }
    }

    // 从 user-service 获取已经处理默认值的连接策略。
    private UserConnectionPolicyVO loadConnectionPolicy(Long userId) {
        ApiResponse<UserConnectionPolicyVO> response = userPolicyClient.getConnectionPolicy(internalToken, userId);

        if (response == null) {
            throw new IllegalStateException("用户连接策略服务没有返回结果");
        }
        if (response.getCode() != 200) {
            throw new IllegalArgumentException("用户连接策略不可用：" + response.getMessage());
        }

        UserConnectionPolicyVO policy = response.getData();
        if (policy == null || !userId.equals(policy.getUserId()) || policy.getMaxConnections() == null || policy.getMaxConnections() < 1) {
            throw new IllegalStateException("用户连接策略返回了非法数据");
        }

        return policy;
    }

    // 获取该用户的 Session 名额分配行锁。
    private void lockSessionAllocation(Long userId) {
        // 首次认证时创建锁行；已经存在时不报错。
        sessionUserGuardMapper.ensureGuardRow(userId);

        Long lockedUserId = sessionUserGuardMapper.selectUserIdForUpdate(userId);

        if (!userId.equals(lockedUserId)) {
            throw new IllegalStateException("用户 Session 名额锁定失败");
        }
    }

    // 检查当前用户是否还有新的 Session 名额。
    private void validateConnectionLimit(Long userId, String currentMac, Integer maxConnections) {
        long activeCount = sessionRecordMapper.countActiveSessionsExcludingMac(userId, currentMac);

        if (activeCount >= maxConnections) {
            throw new IllegalArgumentException("当前账号同时在线设备数已达到上限：" + maxConnections);
        }
    }
}