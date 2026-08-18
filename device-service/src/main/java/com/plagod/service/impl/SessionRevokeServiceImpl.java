package com.plagod.service.impl;

import com.plagod.audit.Audited;
import com.plagod.audit.AuditTargetId;
import com.plagod.audit.AuditTenantId;
import com.plagod.constant.DeviceCommandPurpose;
import com.plagod.constant.SessionStatus;
import com.plagod.entity.device.Esp32Node;
import com.plagod.entity.device.SessionRecord;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.service.DeviceCommandService;
import com.plagod.service.SessionLeaseService;
import com.plagod.service.SessionRevokeService;
import com.plagod.vo.device.SessionRecordVO;
import com.plagod.utils.TenantScopeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Slf4j
@Service
public class SessionRevokeServiceImpl implements SessionRevokeService {

    private static final int SUPER_ADMIN_ROLE = 0;
    private static final int ADMIN_ROLE = 1;

    @Autowired
    private SessionRecordMapper sessionRecordMapper;
    @Autowired
    private Esp32NodeMapper esp32NodeMapper;
    @Autowired
    private SessionLeaseService sessionLeaseService;
    @Autowired
    private DeviceCommandService deviceCommandService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Override
    @Audited(
            action = "session.logout",
            targetType = "SESSION",
            scope = Audited.Scope.TENANT,
            tenantIdSource = Audited.TenantIdSource.ARGUMENT,
            recordDenied = true,
            recordFailed = true)
    @Transactional(
            propagation = Propagation.NOT_SUPPORTED,
            rollbackFor = Exception.class)
    public SessionRecordVO logout(
            @AuditTenantId Long tenantId,
            @AuditTargetId Long sessionId,
            Long userId) {
        TenantScopeUtils.requireTenantId(tenantId);
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户身份无效");
        }
        return revoke(tenantId, sessionId, userId, DeviceCommandPurpose.USER_LOGOUT);
    }

    @Override
    @Audited(
            action = "session.admin-revoke",
            targetType = "SESSION",
            scope = Audited.Scope.TENANT,
            tenantIdSource = Audited.TenantIdSource.ARGUMENT,
            recordDenied = true,
            recordFailed = true)
    @Transactional(
            propagation = Propagation.NOT_SUPPORTED,
            rollbackFor = Exception.class)
    public SessionRecordVO adminRevoke(
            @AuditTenantId Long tenantId,
            @AuditTargetId Long sessionId,
            Integer operatorRole) {
        TenantScopeUtils.requireTenantId(tenantId);
        if (!Integer.valueOf(SUPER_ADMIN_ROLE).equals(operatorRole) && !Integer.valueOf(ADMIN_ROLE).equals(operatorRole)) {
            throw new IllegalArgumentException("当前用户没有管理员撤销权限");
        }
        return revoke(tenantId, sessionId, null, DeviceCommandPurpose.ADMIN_REVOKE);
    }

    private SessionRecordVO revoke(Long tenantId, Long sessionId, Long expectedUserId, String reason) {
        if (sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException("sessionId 必须是有效值");
        }

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        RevokePlan plan = transaction.execute(status ->
                prepareRevoke(tenantId, sessionId, expectedUserId));
        if (plan == null) {
            throw new IllegalStateException("Session 撤销准备事务没有返回结果");
        }
        if (!plan.allocated) {
            return plan.current;
        }

        // 准备事务已提交并释放 Session 行锁，User 调用不会占用本地锁。
        if (plan.requiresFinalSettlement) {
            sessionLeaseService.settleFinalUsage(sessionId);
        }

        SessionRecordVO result = transaction.execute(status ->
                completeRevoke(
                        tenantId, sessionId, expectedUserId, reason));
        if (result == null) {
            throw new IllegalStateException("Session 撤销事务没有返回结果");
        }
        return result;
    }

    private RevokePlan prepareRevoke(
            Long tenantId,
            Long sessionId,
            Long expectedUserId) {
        SessionRecord session = sessionRecordMapper.selectByIdForUpdate(tenantId, sessionId);
        if (session == null) {
            throw ApiStatusException.notFound("Session 不存在");
        }
        if (expectedUserId != null && !expectedUserId.equals(session.getUserId())) {
            throw ApiStatusException.notFound("Session 不存在");
        }

        return new RevokePlan(
                toVO(session),
                SessionStatus.isAllocated(session.getStatus()),
                SessionStatus.isActive(session.getStatus()));
    }

    private SessionRecordVO completeRevoke(
            Long tenantId,
            Long sessionId,
            Long expectedUserId,
            String reason) {
        // 关闭、状态保存和撤销命令入队仍在同一个本地事务内。
        SessionRecord session =
                sessionRecordMapper.selectByIdForUpdate(
                        tenantId, sessionId);
        if (session == null
                || (expectedUserId != null
                && !expectedUserId.equals(session.getUserId()))) {
            throw ApiStatusException.notFound("Session 不存在");
        }
        if (!SessionStatus.isAllocated(session.getStatus())) {
            return toVO(session);
        }
        boolean waitingReplacement = SessionStatus.isWaitingReplacement(session.getStatus());

        LocalDateTime now = LocalDateTime.now();

        session.setStatus(SessionStatus.CLOSED);
        session.setExpireTime(now);
        session.setLogoutTime(now);
        session.setEndReason(reason);

        if (sessionRecordMapper.updateById(session) != 1) {
            throw new IllegalStateException("Session 撤销状态保存失败");
        }

        // WAITING_REPLACEMENT 从未下发 ALLOW，只关闭数据库记录，不发送 REVOKE_ACCESS。
        if (waitingReplacement) {
            return toVO(session);
        }

        // ACTIVE/PENDING 可能已经或即将被固件放行，因此仍然需要撤销命令。
        Esp32Node node = esp32NodeMapper.selectByNodeIdAndTenantIncludeDeleted(
                tenantId, session.getNodeId());
        if (node == null) {
            throw new IllegalStateException("Session 关联的 ESP32 节点不存在");
        }

        deviceCommandService.revokeClientAccess(session.getNodeId(), node.getDeviceCode(), session.getMac(), session.getSessionId(), reason);

        return toVO(session);
    }

    private static final class RevokePlan {
        private final SessionRecordVO current;
        private final boolean allocated;
        private final boolean requiresFinalSettlement;

        private RevokePlan(
                SessionRecordVO current,
                boolean allocated,
                boolean requiresFinalSettlement) {
            this.current = current;
            this.allocated = allocated;
            this.requiresFinalSettlement = requiresFinalSettlement;
        }
    }

    private SessionRecordVO toVO(SessionRecord session) {
        SessionRecordVO result = new SessionRecordVO();
        BeanUtils.copyProperties(session, result);
        return result;
    }
}
