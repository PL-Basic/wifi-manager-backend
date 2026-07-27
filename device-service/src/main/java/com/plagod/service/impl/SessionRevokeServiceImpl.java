package com.plagod.service.impl;

import com.plagod.audit.Audited;
import com.plagod.constant.DeviceCommandPurpose;
import com.plagod.constant.SessionStatus;
import com.plagod.entity.Esp32Node;
import com.plagod.entity.SessionRecord;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.service.DeviceCommandService;
import com.plagod.service.SessionLeaseService;
import com.plagod.service.SessionRevokeService;
import com.plagod.vo.device.SessionRecordVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Audited(action = "session.logout")
    @Transactional(rollbackFor = Exception.class)
    public SessionRecordVO logout(Long sessionId, Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户身份无效");
        }
        return revoke(sessionId, userId, DeviceCommandPurpose.USER_LOGOUT);
    }

    @Override
    @Audited(action = "session.admin-revoke")
    @Transactional(rollbackFor = Exception.class)
    public SessionRecordVO adminRevoke(Long sessionId, Integer operatorRole) {
        if (!Integer.valueOf(SUPER_ADMIN_ROLE).equals(operatorRole) && !Integer.valueOf(ADMIN_ROLE).equals(operatorRole)) {
            throw new IllegalArgumentException("当前用户没有管理员撤销权限");
        }
        return revoke(sessionId, null, DeviceCommandPurpose.ADMIN_REVOKE);
    }

    private SessionRecordVO revoke(Long sessionId, Long expectedUserId, String reason) {
        if (sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException("sessionId 必须是有效值");
        }

        // 行锁串行化：结算、关闭、命令入队不能被续租或重复退出穿插。
        SessionRecord session = sessionRecordMapper.selectByIdForUpdate(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session 不存在");
        }
        if (expectedUserId != null && !expectedUserId.equals(session.getUserId())) {
            throw new IllegalArgumentException("不能退出其他用户的 Session");
        }

        // 重复退出不重复扣费、不重复创建撤销命令，直接返回当前最终状态。
        if (!SessionStatus.isOpen(session.getStatus())) {
            return toVO(session);
        }

        Esp32Node node = esp32NodeMapper.selectById(session.getNodeId());
        if (node == null) {
            throw new IllegalStateException("Session 关联的 ESP32 节点不存在");
        }

        LocalDateTime now = LocalDateTime.now();

        // 仅 ACTIVE 代表固件已确认放行，PENDING 没有可计费的在线时长。
        if (SessionStatus.isActive(session.getStatus())) {
            sessionLeaseService.settleFinalUsage(session, now);
        }

        session.setStatus(SessionStatus.CLOSED);
        session.setExpireTime(now);
        session.setLogoutTime(now);
        session.setEndReason(reason);

        if (sessionRecordMapper.updateById(session) != 1) {
            throw new IllegalStateException("Session 撤销状态保存失败");
        }

        // 与 Session 更新共享当前事务；入队失败时关闭状态也会回滚。
        deviceCommandService.revokeClientAccess(session.getNodeId(), node.getDeviceCode(), session.getMac(), session.getSessionId(), reason);

        return toVO(session);
    }

    private SessionRecordVO toVO(SessionRecord session) {
        SessionRecordVO result = new SessionRecordVO();
        BeanUtils.copyProperties(session, result);
        return result;
    }
}
