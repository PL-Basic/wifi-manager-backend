package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.audit.Audited;
import com.plagod.constant.DeviceCommandPurpose;
import com.plagod.constant.SessionStatus;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.device.PortalAuthorizeDTO;
import com.plagod.dto.user.EntitlementLeaseRequest;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.entity.device.Esp32Node;
import com.plagod.entity.device.MacBlacklist;
import com.plagod.entity.device.SessionRecord;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.*;
import com.plagod.service.ClientSignalQueryService;
import com.plagod.service.DeviceCommandService;
import com.plagod.service.DeviceUserRemoteGateway;
import com.plagod.service.PortalAuthorizationFingerprint;
import com.plagod.service.PortalSessionService;
import com.plagod.service.SessionLeaseService;
import com.plagod.vo.device.SessionRecordVO;
import com.plagod.vo.user.EntitlementLeaseResult;
import com.plagod.web.SafeExceptionLogFormatter;
import com.plagod.vo.user.UserConnectionPolicyVO;
import com.plagod.utils.TenantScopeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PortalSessionServiceImpl implements PortalSessionService {

    // 首次授权只申请短 TTL，后续由续租任务定期刷新
    private static final int INITIAL_LEASE_TTL_SECONDS = 20;
    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");
    private static final Pattern CLIENT_REQUEST_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$");

    @Autowired
    private Esp32NodeMapper esp32NodeMapper;
    @Autowired
    private MacBlacklistMapper macBlacklistMapper;
    @Autowired
    private SessionRecordMapper sessionRecordMapper;
    @Autowired
    private DeviceCommandService deviceCommandService;
    @Autowired
    private DeviceUserRemoteGateway userRemoteGateway;
    @Autowired
    private ClientSignalQueryService clientSignalQueryService;
    @Autowired
    private SessionUserGuardMapper sessionUserGuardMapper;
    @Autowired
    private SessionLeaseService sessionLeaseService;
    @Autowired
    private ClientAccessGuardMapper clientAccessGuardMapper;
    @Autowired
    private DeviceCommandRecordMapper deviceCommandRecordMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    // RSSI 记录允许的最大年龄。默认30秒，避免使用历史记录冒充当前在线客户端。
    @Value("${wifi.portal.client-signal-max-age-seconds:30}")
    private long clientSignalMaxAgeSeconds;

    @Override
    @Audited(
            action = "session.portal-authorize",
            scope = Audited.Scope.TENANT,
            tenantIdSource = Audited.TenantIdSource.REQUEST)
    public SessionRecordVO authorize(Long tenantId, PortalAuthorizeDTO dto, Long userId) {
        TenantScopeUtils.requireTenantId(tenantId);
        if (dto == null || userId == null || userId <= 0) {
            throw new IllegalArgumentException("Portal 授权参数或者用户身份无效");
        }

        String deviceCode = cleanRequired(dto.getDeviceCode(), "设备编码 deviceCode 不能为空");
        String ip = cleanRequired(dto.getIp(), "客户端 IP 不能为空");
        String mac = normalizeMac(dto.getMac());
        if (mac == null) {
            throw new IllegalArgumentException("客户端 MAC 格式不正确");
        }
        String clientRequestId = requireClientRequestId(dto.getClientRequestId());
        String requestFingerprint =
                PortalAuthorizationFingerprint.calculate(tenantId, userId, dto);

        DeviceCommandRecord completedReceipt =
                deviceCommandRecordMapper.selectPortalAuthorizationReceipt(
                        tenantId, userId, clientRequestId);
        if (completedReceipt != null) {
            requireMatchingFingerprint(
                    requestFingerprint, completedReceipt.getRequestFingerprint());
            return replaySession(tenantId, userId, completedReceipt.getSessionId());
        }

        SessionRecord waitingReplay =
                sessionRecordMapper.selectByAuthorizeRequest(
                        tenantId, userId, clientRequestId);
        if (waitingReplay != null
                && SessionStatus.isWaitingReplacement(
                waitingReplay.getStatus())) {
            requireMatchingFingerprint(
                    requestFingerprint,
                    waitingReplay.getRequestFingerprint());
            return toVO(waitingReplay);
        }

        UserConnectionPolicyVO connectionPolicy = loadConnectionPolicy(userId);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        AuthorizationPlan plan = prepareWithSettlements(
                transaction,
                tenantId,
                userId,
                dto,
                deviceCode,
                ip,
                mac,
                clientRequestId,
                requestFingerprint,
                connectionPolicy);
        if (plan == null) {
            throw new IllegalStateException("Portal 本地准备事务没有返回结果");
        }
        if (!plan.requiresLease) {
            return replaySession(tenantId, userId, plan.sessionId);
        }

        EntitlementLeaseResult lease =
                acquireInitialLease(
                        tenantId,
                        userId,
                        plan.sessionId,
                        clientRequestId);
        try {
            validateLease(lease);
        } catch (IllegalArgumentException exception) {
            transaction.executeWithoutResult(status ->
                    closePreparedAuthorization(plan, "ENTITLEMENT_DENIED"));
            throw exception;
        }

        completeWithSettlements(transaction, plan, lease);
        return replaySession(tenantId, userId, plan.sessionId);
    }

    private AuthorizationPlan prepareWithSettlements(
            TransactionTemplate transaction,
            Long tenantId,
            Long userId,
            PortalAuthorizeDTO dto,
            String deviceCode,
            String ip,
            String mac,
            String clientRequestId,
            String requestFingerprint,
            UserConnectionPolicyVO connectionPolicy) {
        Set<Long> settledSessions = new HashSet<>();
        while (true) {
            try {
                return transaction.execute(status ->
                        prepareAuthorization(
                                tenantId,
                                userId,
                                dto,
                                deviceCode,
                                ip,
                                mac,
                                clientRequestId,
                                requestFingerprint,
                                connectionPolicy));
            } catch (FinalSettlementRequiredException exception) {
                settleOutsideTransaction(
                        exception.sessionId, settledSessions);
            }
        }
    }

    private void completeWithSettlements(
            TransactionTemplate transaction,
            AuthorizationPlan plan,
            EntitlementLeaseResult lease) {
        Set<Long> settledSessions = new HashSet<>();
        while (true) {
            try {
                transaction.executeWithoutResult(status ->
                        completeAuthorization(plan, lease));
                return;
            } catch (FinalSettlementRequiredException exception) {
                settleOutsideTransaction(
                        exception.sessionId, settledSessions);
            }
        }
    }

    private void settleOutsideTransaction(
            Long sessionId,
            Set<Long> settledSessions) {
        if (sessionId == null || !settledSessions.add(sessionId)) {
            throw new IllegalStateException(
                    "Portal 最终结算后 Session 计费基准未推进");
        }
        sessionLeaseService.settleFinalUsage(sessionId);
    }

    private AuthorizationPlan prepareAuthorization(
            Long tenantId,
            Long userId,
            PortalAuthorizeDTO dto,
            String deviceCode,
            String ip,
            String mac,
            String clientRequestId,
            String requestFingerprint,
            UserConnectionPolicyVO connectionPolicy) {
        // 从黑名单检查开始，到 Session 创建和命令入队结束，
        // 同一个 MAC 只能存在一个授权或管控事务。
        lockClientAccess(tenantId, mac);

        // 锁住节点行，使同一 ESP32 上的 Portal 授权请求串行执行。
        Esp32Node node = esp32NodeMapper.selectByDeviceCodeForUpdateAndTenantIncludeDeleted(tenantId, deviceCode);
        if (node == null || Integer.valueOf(1).equals(node.getDelFlag())) {
            throw ApiStatusException.notFound("Portal 所连接的设备不存在或已退役");
        }
        if (!Integer.valueOf(1).equals(node.getStatus())) {
            throw new IllegalArgumentException("Portal 所连接设备当前不在线");
        }

        LocalDateTime now = LocalDateTime.now();

        // 永久黑名单或者尚未过期的临时黑名单都会阻止认证
        QueryWrapper<MacBlacklist> blackListQuery = new QueryWrapper<>();
        blackListQuery.eq("tenant_id", tenantId)
                .eq("mac", mac)
                .and(wrapper -> wrapper.isNull("expire_time")
                        .or().gt("expire_time", now));
        if (macBlacklistMapper.selectCount(blackListQuery) > 0) {
            throw new IllegalArgumentException("该客户端已被加入黑名单");
        }

        validateRecentClientSignal(tenantId, node, deviceCode, mac, now);

        SessionRecord reusableSession =
                sessionRecordMapper.selectByAuthorizeRequestForUpdate(
                        tenantId, userId, clientRequestId);
        boolean sameRequestInProgress = reusableSession != null;
        if (reusableSession != null) {
            requireMatchingFingerprint(
                    requestFingerprint, reusableSession.getRequestFingerprint());
        } else {
            reusableSession =
                    findReusableOpenSession(
                            tenantId, userId, node.getNodeId(), mac);
        }

        if (reusableSession != null) {
            reusableSession.setClientRequestId(clientRequestId);
            reusableSession.setRequestFingerprint(requestFingerprint);

            // 撤销命令还没有成功，重复请求只返回当前状态。
            if (SessionStatus.isWaitingReplacement(reusableSession.getStatus())) {
                if (!sameRequestInProgress
                        && sessionRecordMapper.updateById(reusableSession) != 1) {
                    throw new IllegalStateException("Portal 幂等状态保存失败");
                }
                return new AuthorizationPlan(
                        tenantId,
                        userId,
                        reusableSession.getSessionId(),
                        deviceCode,
                        mac,
                        clientRequestId,
                        requestFingerprint,
                        false);
            }

            reusableSession.setIp(ip);

            String deviceInfo = cleanNullable(dto.getDeviceInfo());
            if (deviceInfo != null) {
                reusableSession.setDeviceInfo(deviceInfo);
            }

            if (sessionRecordMapper.updateById(reusableSession) != 1) {
                throw new IllegalStateException("Portal 幂等准备状态保存失败");
            }
            return new AuthorizationPlan(
                    tenantId,
                    userId,
                    reusableSession.getSessionId(),
                    deviceCode,
                    mac,
                    clientRequestId,
                    requestFingerprint,
                    true);
        }

        // 串行化同一用户的“统计名额并创建 Session”流程。
        lockSessionAllocation(tenantId, userId);

        // 当前 MAC 如果正在其他节点使用，随后会替换旧 Session，
        // 因此只统计其他 MAC 占用的名额。
        Long replacedSessionId = prepareConnectionSlot(tenantId, userId, mac, connectionPolicy.getMaxConnections(), Boolean.TRUE.equals(dto.getForceReplaceOldest()), now);

        SessionRecord sessionRecord = new SessionRecord();
        sessionRecord.setTenantId(tenantId);
        sessionRecord.setUserId(userId);
        sessionRecord.setClientRequestId(clientRequestId);
        sessionRecord.setRequestFingerprint(requestFingerprint);
        sessionRecord.setNodeId(node.getNodeId());
        sessionRecord.setReplacedSessionId(replacedSessionId);
        sessionRecord.setMac(mac);
        sessionRecord.setIp(ip);
        sessionRecord.setDeviceInfo(cleanNullable(dto.getDeviceInfo()));
        sessionRecord.setLoginTime(now);
        sessionRecord.setExpireTime(now);
        // 新 Session 只有在 ESP32 返回成功后才能进入 ACTIVE。
        sessionRecord.setStatus(replacedSessionId == null ? SessionStatus.PENDING : SessionStatus.WAITING_REPLACEMENT);
        sessionRecord.setBytesUp(0L);
        sessionRecord.setBytesDown(0L);
        sessionRecord.setConsumedSeconds(0L);

        if (sessionRecordMapper.insert(sessionRecord) != 1 || sessionRecord.getSessionId() == null) {
            throw new IllegalStateException("Portal 会话创建失败");
        }

        // 必须等待旧 Session 的撤销结果，当前不能生成 ALLOW。
        if (replacedSessionId != null) {
            return new AuthorizationPlan(
                    tenantId,
                    userId,
                    sessionRecord.getSessionId(),
                    deviceCode,
                    mac,
                    clientRequestId,
                    requestFingerprint,
                    false);
        }

        return new AuthorizationPlan(
                tenantId,
                userId,
                sessionRecord.getSessionId(),
                deviceCode,
                mac,
                clientRequestId,
                requestFingerprint,
                true);
    }

    private void completeAuthorization(
            AuthorizationPlan plan,
            EntitlementLeaseResult lease) {
        lockClientAccess(plan.tenantId, plan.mac);

        DeviceCommandRecord receipt =
                deviceCommandRecordMapper.selectPortalAuthorizationReceipt(
                        plan.tenantId,
                        plan.userId,
                        plan.clientRequestId);
        if (receipt != null) {
            requireMatchingFingerprint(
                    plan.requestFingerprint,
                    receipt.getRequestFingerprint());
            return;
        }

        Esp32Node node =
                esp32NodeMapper.selectByDeviceCodeForUpdateAndTenantIncludeDeleted(
                        plan.tenantId, plan.deviceCode);
        if (node == null
                || Integer.valueOf(1).equals(node.getDelFlag())
                || !Integer.valueOf(1).equals(node.getStatus())) {
            throw new IllegalStateException("Portal 完成授权时设备已不可用");
        }

        SessionRecord session =
                sessionRecordMapper.selectByIdForUpdate(
                        plan.tenantId, plan.sessionId);
        requirePreparedSession(plan, session);
        if (SessionStatus.isWaitingReplacement(session.getStatus())) {
            return;
        }
        if (Integer.valueOf(SessionStatus.CLOSED).equals(session.getStatus())) {
            throw new IllegalStateException("Portal 准备 Session 已关闭");
        }

        LocalDateTime now = LocalDateTime.now();
        closeConflictingSessions(
                plan.tenantId, plan.mac, plan.sessionId, now);
        applyLease(session, lease, now);
        saveAndEnqueue(
                plan.deviceCode,
                session,
                lease.getTtlSeconds(),
                plan.userId,
                plan.clientRequestId,
                plan.requestFingerprint);
    }

    private void closePreparedAuthorization(
            AuthorizationPlan plan,
            String reason) {
        SessionRecord session =
                sessionRecordMapper.selectByIdForUpdate(
                        plan.tenantId, plan.sessionId);
        if (session == null) {
            return;
        }
        requirePreparedSession(plan, session);
        if (SessionStatus.isActive(session.getStatus())
                || SessionStatus.isWaitingReplacement(session.getStatus())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        session.setStatus(SessionStatus.CLOSED);
        session.setExpireTime(now);
        session.setLogoutTime(now);
        session.setEndReason(reason);
        if (sessionRecordMapper.updateById(session) != 1) {
            throw new IllegalStateException("Portal 拒绝状态保存失败");
        }
    }

    private void requirePreparedSession(
            AuthorizationPlan plan,
            SessionRecord session) {
        if (session == null
                || !plan.userId.equals(session.getUserId())
                || !plan.clientRequestId.equals(session.getClientRequestId())) {
            throw new IllegalStateException("Portal 准备 Session 不存在");
        }
        requireMatchingFingerprint(
                plan.requestFingerprint, session.getRequestFingerprint());
    }

    @Override
    public void activateWaitingReplacement(Long tenantId, Long replacedSessionId) {
        TenantScopeUtils.requireTenantId(tenantId);
        if (replacedSessionId == null || replacedSessionId <= 0) {
            throw new IllegalArgumentException("被替换 SessionId 无效");
        }

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        AuthorizationPlan plan = transaction.execute(status ->
                prepareWaitingReplacement(tenantId, replacedSessionId));
        if (plan == null) {
            return;
        }

        EntitlementLeaseResult lease;
        try {
            lease = acquireInitialLease(
                    plan.tenantId,
                    plan.userId,
                    plan.sessionId,
                    plan.clientRequestId);
            validateLease(lease);
        } catch (IllegalArgumentException exception) {
            transaction.executeWithoutResult(status ->
                    closePreparedReplacement(
                            plan, "REPLACEMENT_ENTITLEMENT_DENIED"));
            return;
        } catch (RuntimeException exception) {
            // 旧授权已经撤销成功，不能因权益服务临时异常回滚该 command-result。
            // 关闭等待 Session 后允许用户重新发起认证。
            log.warn(
                    "强制替换撤销成功，但新 Session 权益暂时不可用，sessionId={}, type={}, safeStack={}",
                    plan.sessionId,
                    exception.getClass().getName(),
                    SafeExceptionLogFormatter.format(exception));
            transaction.executeWithoutResult(status ->
                    closePreparedReplacement(
                            plan, "REPLACEMENT_ENTITLEMENT_UNAVAILABLE"));
            return;
        }

        transaction.executeWithoutResult(status ->
                completeWaitingReplacement(plan, lease));
    }

    private AuthorizationPlan prepareWaitingReplacement(
            Long tenantId,
            Long replacedSessionId) {
        SessionRecord waiting =
                sessionRecordMapper.selectWaitingReplacementForUpdate(
                        tenantId, replacedSessionId);
        // 重复 command-result 或等待 Session 已取消时直接幂等返回。
        if (waiting == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        Esp32Node node =
                esp32NodeMapper.selectByNodeIdAndTenantIncludeDeleted(
                        tenantId, waiting.getNodeId());
        if (node == null
                || Integer.valueOf(1).equals(node.getDelFlag())
                || !Integer.valueOf(1).equals(node.getStatus())
                || !StringUtils.hasText(node.getDeviceCode())) {
            closeWaitingSession(
                    waiting, now, "REPLACEMENT_NODE_UNAVAILABLE");
            return null;
        }

        String clientRequestId =
                requireClientRequestId(waiting.getClientRequestId());
        String requestFingerprint =
                requireRequestFingerprint(waiting.getRequestFingerprint());
        return new AuthorizationPlan(
                tenantId,
                waiting.getUserId(),
                waiting.getSessionId(),
                node.getDeviceCode(),
                waiting.getMac(),
                clientRequestId,
                requestFingerprint,
                true);
    }

    private void closePreparedReplacement(
            AuthorizationPlan plan,
            String reason) {
        SessionRecord waiting =
                sessionRecordMapper.selectByIdForUpdate(
                        plan.tenantId, plan.sessionId);
        if (waiting == null
                || !SessionStatus.isWaitingReplacement(
                waiting.getStatus())) {
            return;
        }
        requirePreparedSession(plan, waiting);
        closeWaitingSession(waiting, LocalDateTime.now(), reason);
    }

    private void completeWaitingReplacement(
            AuthorizationPlan plan,
            EntitlementLeaseResult lease) {
        lockClientAccess(plan.tenantId, plan.mac);
        SessionRecord waiting =
                sessionRecordMapper.selectByIdForUpdate(
                        plan.tenantId, plan.sessionId);
        if (waiting == null
                || !SessionStatus.isWaitingReplacement(
                waiting.getStatus())) {
            return;
        }
        requirePreparedSession(plan, waiting);

        Esp32Node node =
                esp32NodeMapper.selectByDeviceCodeForUpdateAndTenantIncludeDeleted(
                        plan.tenantId, plan.deviceCode);
        LocalDateTime now = LocalDateTime.now();
        if (node == null
                || Integer.valueOf(1).equals(node.getDelFlag())
                || !Integer.valueOf(1).equals(node.getStatus())) {
            closeWaitingSession(
                    waiting, now, "REPLACEMENT_NODE_UNAVAILABLE");
            return;
        }

        // applyLease 会把 WAITING_REPLACEMENT 转为 PENDING。
        applyLease(waiting, lease, now);
        saveAndEnqueue(
                plan.deviceCode,
                waiting,
                lease.getTtlSeconds(),
                plan.userId,
                plan.clientRequestId,
                plan.requestFingerprint);
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
    private EntitlementLeaseResult acquireInitialLease(
            Long tenantId,
            Long userId,
            Long sessionId,
            String clientRequestId) {
        EntitlementLeaseRequest request = new EntitlementLeaseRequest();

        request.setRequestId(
                buildEntitlementRequestId(userId, clientRequestId));
        request.setUserId(userId);
        request.setSessionId(sessionId);
        request.setUsageSeconds(0L);
        request.setRequestedTtlSeconds(INITIAL_LEASE_TTL_SECONDS);

        ApiResponse<EntitlementLeaseResult> response =
                userRemoteGateway.acquireLease(request);

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

    private String buildEntitlementRequestId(
            Long userId,
            String clientRequestId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户身份无效");
        }
        String normalizedRequestId =
                requireClientRequestId(clientRequestId);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedRequestId.getBytes(
                            StandardCharsets.UTF_8));
            StringBuilder suffix = new StringBuilder(32);
            for (int index = 0; index < 16; index++) {
                suffix.append(String.format(
                        Locale.ROOT, "%02x", digest[index] & 0xff));
            }
            return "portal-u" + userId + "-" + suffix;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "运行环境缺少 SHA-256", exception);
        }
    }

    // ACTIVE 或 PENDING Session 都可以被相同认证请求复用。
    private SessionRecord findReusableOpenSession(Long tenantId, Long userId, Long nodeId, String mac) {

        QueryWrapper<SessionRecord> query = new QueryWrapper<>();

        query.eq("tenant_id", tenantId)
                .eq("user_id", userId)
                .eq("node_id", nodeId)
                .eq("mac", mac)
                .in("status", SessionStatus.ACTIVE, SessionStatus.PENDING, SessionStatus.WAITING_REPLACEMENT)
                .orderByDesc("session_id")
                .last("limit 1 for update");

        return sessionRecordMapper.selectOne(query);
    }

    // 关闭同一 MAC 的其他已分配 Session，并收口其计费和固件授权。
    private void closeConflictingSessions(Long tenantId, String mac, Long keepSessionId, LocalDateTime now) {
        List<SessionRecord> sessions = sessionRecordMapper.selectAllocatedByMacForUpdate(tenantId, mac);

        for (SessionRecord session : sessions) {
            if (keepSessionId != null && keepSessionId.equals(session.getSessionId())) {
                continue;
            }

            boolean waitingReplacement = SessionStatus.isWaitingReplacement(session.getStatus());

            if (SessionStatus.isActive(session.getStatus())) {
                requireFinalSettlementCompleted(session, now);
            }

            session.setStatus(SessionStatus.CLOSED);
            session.setExpireTime(now);
            session.setLogoutTime(now);
            session.setEndReason("PORTAL_REPLACED");

            if (sessionRecordMapper.updateById(session) != 1) {
                throw new IllegalStateException("Portal 冲突 Session 关闭失败，sessionId=" + session.getSessionId());
            }

            // WAITING_REPLACEMENT 从未发送 ALLOW，不存在需要撤销的固件授权。
            if (waitingReplacement) {
                continue;
            }

            Esp32Node oldNode = esp32NodeMapper.selectByNodeIdAndTenantIncludeDeleted(tenantId, session.getNodeId());
            if (oldNode == null || !StringUtils.hasText(oldNode.getDeviceCode())) {
                throw new IllegalStateException("Portal 冲突 Session 关联节点不存在，sessionId=" + session.getSessionId());
            }

            deviceCommandService.revokeClientAccess(
                    session.getNodeId(),
                    oldNode.getDeviceCode(),
                    session.getMac(),
                    session.getSessionId(),
                    DeviceCommandPurpose.PORTAL_CONFLICT_REVOKE);
        }
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
        // 已经确认过的 ACTIVE Session 在重复认证期间继续保持 ACTIVE。
        // 新建或仍待确认的 Session 保持 PENDING，等待 command-result。
        if (!SessionStatus.isActive(session.getStatus())) {
            session.setStatus(SessionStatus.PENDING);
        }
        session.setLogoutTime(null);
        session.setEndReason(null);

        // 重复认证不能重置该字段，否则会漏掉尚未结算的在线时长。
        if (session.getLastBilledTime() == null) {
            session.setLastBilledTime(now);
        }
    }

    // 保存 Session，然后使用同一个 sessionId 刷新 ESP32 的短 TTL。
    private SessionRecordVO saveAndEnqueue(
            String deviceCode,
            SessionRecord session,
            Integer ttlSeconds,
            Long actorUserId,
            String clientRequestId,
            String requestFingerprint) {
        if (sessionRecordMapper.updateById(session) != 1) {
            throw new IllegalStateException("Portal 会话状态更新失败");
        }

        deviceCommandService.allowClient(
                session.getNodeId(),
                deviceCode,
                session.getMac(),
                session.getSessionId(),
                ttlSeconds,
                actorUserId,
                clientRequestId,
                requestFingerprint);
        SessionRecordVO result = new SessionRecordVO();
        BeanUtils.copyProperties(session, result);
        return result;
    }

    // 校验该 MAC 最近是否被当前 ESP32 节点实际观察到
    private void validateRecentClientSignal(Long tenantId, Esp32Node node, String deviceCode, String mac, LocalDateTime now) {
        if (clientSignalMaxAgeSeconds <= 0) {
            throw new IllegalStateException("Portal RSSI 时间窗口配置必须大于 0");
        }
        // 只接受当前时间窗口内由后端记录的 RSSI。
        LocalDateTime sinceTime = now.minusSeconds(clientSignalMaxAgeSeconds);

        boolean observed = clientSignalQueryService.wasRecentlyObserved(tenantId, node.getNodeId(), deviceCode, mac, sinceTime);
        if (!observed) {
            throw new IllegalArgumentException("当前 ESP32 最近未观察到该客户端，请确认仍连接热点后重试");
        }
    }

    // 从 user-service 获取已经处理默认值的连接策略。
    private UserConnectionPolicyVO loadConnectionPolicy(Long userId) {
        ApiResponse<UserConnectionPolicyVO> response =
                userRemoteGateway.getConnectionPolicy(userId);

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
    private void lockSessionAllocation(Long tenantId, Long userId) {
        // 首次认证时创建锁行；已经存在时不报错。
        sessionUserGuardMapper.ensureGuardRow(tenantId, userId);

        Long lockedUserId = sessionUserGuardMapper.selectUserIdForUpdate(tenantId, userId);

        if (!userId.equals(lockedUserId)) {
            throw new IllegalStateException("用户 Session 名额锁定失败");
        }
    }

    private Long prepareConnectionSlot(Long tenantId, Long userId, String currentMac, Integer maxConnections, boolean forceReplaceOldest, LocalDateTime now) {

        long allocatedCount = sessionRecordMapper.countAllocatedSessionsExcludingMac(tenantId, userId, currentMac);

        if (allocatedCount < maxConnections) {
            return null;
        }

        if (!forceReplaceOldest) {
            throw new IllegalArgumentException("当前账号同时在线设备数已达到上限：" + maxConnections + "，确认后可强制替换最旧 Session");
        }

        SessionRecord oldest = sessionRecordMapper.selectOldestOpenSessionForUpdate(tenantId, userId, currentMac);

        if (oldest == null) {
            throw new IllegalStateException("连接名额已满，但没有可替换的开放 Session");
        }

        Esp32Node oldNode = esp32NodeMapper.selectByNodeIdAndTenantIncludeDeleted(
                        tenantId, oldest.getNodeId());

        if (oldNode == null || !StringUtils.hasText(oldNode.getDeviceCode())) {
            throw new IllegalStateException("最旧 Session 关联的 ESP32 节点不存在");
        }

        // 只有 ACTIVE Session 存在需要结算的真实在线时间。
        if (SessionStatus.isActive(oldest.getStatus())) {
            requireFinalSettlementCompleted(oldest, now);
        }

        oldest.setStatus(SessionStatus.CLOSED);
        oldest.setExpireTime(now);
        oldest.setLogoutTime(now);
        oldest.setEndReason("FORCE_LOGIN_REPLACED");

        if (sessionRecordMapper.updateById(oldest) != 1) {
            throw new IllegalStateException("最旧 Session 关闭失败");
        }

        // 撤销命令与旧 Session 关闭处于当前本地事务中。
        deviceCommandService.revokeClientAccess(oldest.getNodeId(), oldNode.getDeviceCode(), oldest.getMac(), oldest.getSessionId(), DeviceCommandPurpose.FORCE_LOGIN_REPLACE);

        return oldest.getSessionId();
    }

    private void closeWaitingSession(SessionRecord session, LocalDateTime now, String reason) {
        session.setStatus(SessionStatus.CLOSED);
        session.setExpireTime(now);
        session.setLogoutTime(now);
        session.setEndReason(reason);

        if (sessionRecordMapper.updateById(session) != 1) {
            throw new IllegalStateException("等待替换的 Session 关闭失败");
        }
    }

    private SessionRecordVO toVO(SessionRecord session) {
        SessionRecordVO result = new SessionRecordVO();
        BeanUtils.copyProperties(session, result);
        return result;
    }

    private void lockClientAccess(Long tenantId, String mac) {
        clientAccessGuardMapper.ensureGuardRow(tenantId, mac);
        String lockedMac = clientAccessGuardMapper.selectMacForUpdate(tenantId, mac);

        if (!mac.equals(lockedMac)) {
            throw new IllegalStateException("客户端访问状态锁定失败");
        }
    }

    private String requireClientRequestId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("clientRequestId 不能为空");
        }
        String cleaned = value.trim();
        if (!CLIENT_REQUEST_ID_PATTERN.matcher(cleaned).matches()) {
            throw new IllegalArgumentException("clientRequestId 格式不正确");
        }
        return cleaned;
    }

    private void requireMatchingFingerprint(String requested, String stored) {
        if (!StringUtils.hasText(stored)) {
            throw new IllegalStateException("Portal 幂等记录缺少 fingerprint");
        }
        if (!stored.equals(requested)) {
            throw ApiStatusException.idempotencyConflict(
                    "clientRequestId 已用于不同的 Portal 授权输入");
        }
    }

    private String requireRequestFingerprint(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Portal 幂等记录缺少 fingerprint");
        }
        return value;
    }

    private SessionRecordVO replaySession(
            Long tenantId,
            Long userId,
            Long sessionId) {
        if (sessionId == null) {
            throw new IllegalStateException("Portal 幂等记录缺少 sessionId");
        }
        SessionRecord session =
                sessionRecordMapper.selectOwnedById(tenantId, userId, sessionId);
        if (session == null) {
            throw new IllegalStateException("Portal 幂等记录关联 Session 不存在");
        }
        return toVO(session);
    }

    private void requireFinalSettlementCompleted(
            SessionRecord session,
            LocalDateTime now) {
        if (session.getLastSeenTime() == null) {
            return;
        }
        LocalDateTime billedTime = session.getLastBilledTime();
        if (billedTime == null) {
            billedTime = session.getLoginTime();
        }
        LocalDateTime observedTime =
                session.getLastSeenTime().isAfter(now)
                        ? now
                        : session.getLastSeenTime();
        if (billedTime != null && observedTime.isAfter(billedTime)) {
            throw new FinalSettlementRequiredException(
                    session.getSessionId());
        }
    }

    private static final class AuthorizationPlan {
        private final Long tenantId;
        private final Long userId;
        private final Long sessionId;
        private final String deviceCode;
        private final String mac;
        private final String clientRequestId;
        private final String requestFingerprint;
        private final boolean requiresLease;

        private AuthorizationPlan(
                Long tenantId,
                Long userId,
                Long sessionId,
                String deviceCode,
                String mac,
                String clientRequestId,
                String requestFingerprint,
                boolean requiresLease) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.sessionId = sessionId;
            this.deviceCode = deviceCode;
            this.mac = mac;
            this.clientRequestId = clientRequestId;
            this.requestFingerprint = requestFingerprint;
            this.requiresLease = requiresLease;
        }
    }

    private static final class FinalSettlementRequiredException
            extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Long sessionId;

        private FinalSettlementRequiredException(Long sessionId) {
            super("Portal Session 需要事务外最终结算");
            this.sessionId = sessionId;
        }
    }
}
