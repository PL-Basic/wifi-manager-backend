package com.plagod.service.impl;

import com.plagod.client.UserEntitlementClient;
import com.plagod.constant.DeviceCommandPurpose;
import com.plagod.constant.DeviceCommandStatus;
import com.plagod.constant.SessionStatus;
import com.plagod.dto.ApiResponse;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.entity.device.Esp32Node;
import com.plagod.entity.device.SessionRecord;
import com.plagod.mapper.DeviceCommandRecordMapper;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.service.PortalSessionStatusQueryService;
import com.plagod.vo.portal.PortalSessionStatusVO;
import com.plagod.vo.user.EntitlementSnapshotVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PortalSessionStatusQueryServiceImpl implements PortalSessionStatusQueryService {

    @Autowired
    private SessionRecordMapper sessionRecordMapper;

    @Autowired
    private Esp32NodeMapper esp32NodeMapper;

    @Autowired
    private DeviceCommandRecordMapper commandRecordMapper;

    @Autowired
    private UserEntitlementClient userEntitlementClient;

    @Value("${wifi.internal.token}")
    private String internalToken;

    @Override
    public PortalSessionStatusVO getOwnedStatus(Long sessionId, Long userId) {
        if (sessionId == null || sessionId <= 0 || userId == null || userId <= 0) {
            throw new IllegalArgumentException("Session 或用户身份无效");
        }

        SessionRecord session = sessionRecordMapper.selectById(sessionId);

        // 不区分不存在和不属于本人，避免枚举其他用户 Session。
        if (session == null || !userId.equals(session.getUserId())) {
            throw new IllegalArgumentException("Session 不存在或无权访问");
        }

        Esp32Node node = esp32NodeMapper.selectByNodeIdIncludeDeleted(session.getNodeId());

        if (node == null) {
            throw new IllegalStateException("Session 关联的 ESP32 节点不存在");
        }

        DeviceCommandRecord command = findDisplayCommand(session);
        EntitlementSnapshotVO entitlement = loadEntitlementSnapshot(session);

        PortalSessionStatusVO result = new PortalSessionStatusVO();
        result.setSessionId(session.getSessionId());
        result.setSessionStatusCode(session.getStatus());
        result.setSessionStatus(resolveSessionStatus(session.getStatus()));
        result.setStatusMessage(resolveStatusMessage(session, command));

        result.setDeviceCode(node.getDeviceCode());
        result.setHotspotName(node.getName());

        result.setAuthorizationMode(session.getAuthorizationMode());
        result.setLeaseExpireTime(session.getExpireTime());
        result.setReplacedSessionId(session.getReplacedSessionId());
        result.setEndReason(session.getEndReason());

        applyEntitlement(result, entitlement);
        applyCommand(result, command);
        return result;
    }

    private DeviceCommandRecord findDisplayCommand(SessionRecord session) {
        DeviceCommandRecord allow = commandRecordMapper.selectLatestSessionAllowCommand(session.getSessionId());

        if (allow != null) {
            return allow;
        }

        if (session.getReplacedSessionId() == null) {
            return null;
        }

        return commandRecordMapper.selectLatestForceReplacementCommand(session.getReplacedSessionId());
    }

    private EntitlementSnapshotVO loadEntitlementSnapshot(SessionRecord session) {
        if (session.getEntitlementId() == null) {
            return null;
        }

        ApiResponse<EntitlementSnapshotVO> response;
        try {
            response = userEntitlementClient.getSnapshot(internalToken, session.getUserId(), session.getEntitlementId());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("权益快照服务暂时不可用", exception);
        }

        if (response == null || response.getCode() != 200 || response.getData() == null) {
            throw new IllegalStateException("权益快照查询失败");
        }

        EntitlementSnapshotVO snapshot = response.getData();

        if (!session.getUserId().equals(snapshot.getUserId()) || !session.getEntitlementId().equals(snapshot.getEntitlementId())) {
            throw new IllegalStateException("权益快照与 Session 关联不一致");
        }

        return snapshot;
    }

    private void applyEntitlement(PortalSessionStatusVO result, EntitlementSnapshotVO entitlement) {
        if (entitlement == null) {
            return;
        }

        if (!StringUtils.hasText(result.getAuthorizationMode())) {
            result.setAuthorizationMode(entitlement.getMode());
        }

        result.setEntitlementStatus(entitlement.getStatus());
        result.setRemainingSeconds(entitlement.getRemainingSeconds());
        result.setSubscriptionEndTime(entitlement.getSubscriptionEndTime());
    }

    private void applyCommand(PortalSessionStatusVO result, DeviceCommandRecord command) {
        if (command == null) {
            return;
        }

        result.setCommandRequestId(command.getRequestId());
        result.setCommandType(command.getCommandType());
        result.setCommandPurpose(command.getPurpose());
        result.setCommandStatusCode(command.getStatus());
        result.setCommandStatus(resolveCommandStatus(command.getStatus()));
        result.setCommandResultMessage(command.getResultMessage());
        result.setCommandPublishTime(command.getPublishTime());
        result.setCommandResultTime(command.getResultTime());
    }

    private String resolveSessionStatus(Integer status) {
        if (Integer.valueOf(SessionStatus.CLOSED).equals(status)) {
            return "CLOSED";
        }
        if (Integer.valueOf(SessionStatus.ACTIVE).equals(status)) {
            return "ACTIVE";
        }
        if (Integer.valueOf(SessionStatus.PENDING).equals(status)) {
            return "PENDING";
        }
        if (Integer.valueOf(SessionStatus.WAITING_REPLACEMENT).equals(status)) {
            return "WAITING_REPLACEMENT";
        }
        return "UNKNOWN";
    }

    private String resolveCommandStatus(Integer status) {
        if (Integer.valueOf(DeviceCommandStatus.PENDING).equals(status)) {
            return "PENDING";
        }
        if (Integer.valueOf(DeviceCommandStatus.PUBLISHED).equals(status)) {
            return "PUBLISHED";
        }
        if (Integer.valueOf(DeviceCommandStatus.SUCCEEDED).equals(status)) {
            return "SUCCEEDED";
        }
        if (Integer.valueOf(DeviceCommandStatus.EXECUTION_FAILED).equals(status)) {
            return "EXECUTION_FAILED";
        }
        if (Integer.valueOf(DeviceCommandStatus.PUBLISH_FAILED).equals(status)) {
            return "PUBLISH_FAILED";
        }
        if (Integer.valueOf(DeviceCommandStatus.TIMED_OUT).equals(status)) {
            return "TIMED_OUT";
        }
        return "UNKNOWN";
    }

    private String resolveStatusMessage(SessionRecord session, DeviceCommandRecord command) {
        if (SessionStatus.isWaitingReplacement(session.getStatus())) {
            return commandProgress("正在撤销旧设备授权", command);
        }

        if (SessionStatus.isPending(session.getStatus())) {
            return commandProgress("正在向 ESP32 申请客户端授权", command);
        }

        if (SessionStatus.isActive(session.getStatus())) {
            if (command != null
                    && DeviceCommandPurpose.LEASE_RENEW.equals(command.getPurpose())
                    && (Integer.valueOf(DeviceCommandStatus.PENDING).equals(command.getStatus())
                    || Integer.valueOf(DeviceCommandStatus.PUBLISHED).equals(command.getStatus()))) {
                return "ESP32 已确认授权，当前正在刷新短租约";
            }
            return "ESP32 已确认客户端授权成功";
        }

        if (Integer.valueOf(SessionStatus.CLOSED).equals(session.getStatus())) {
            return StringUtils.hasText(session.getEndReason()) ? "Session 已结束，原因：" + session.getEndReason() : "Session 已结束";
        }

        return "Session 状态未知";
    }

    private String commandProgress(String prefix, DeviceCommandRecord command) {
        if (command == null) {
            return prefix + "，命令尚未生成";
        }

        Integer status = command.getStatus();

        if (Integer.valueOf(DeviceCommandStatus.PENDING).equals(status)) {
            return prefix + "，命令已进入发送队列";
        }
        if (Integer.valueOf(DeviceCommandStatus.PUBLISHED).equals(status)) {
            return prefix + "，等待 ESP32 执行结果";
        }
        if (Integer.valueOf(DeviceCommandStatus.SUCCEEDED).equals(status)) {
            return prefix + "，ESP32 已确认执行成功";
        }
        if (Integer.valueOf(DeviceCommandStatus.EXECUTION_FAILED).equals(status)) {
            return prefix + "，ESP32 执行失败";
        }
        if (Integer.valueOf(DeviceCommandStatus.PUBLISH_FAILED).equals(status)) {
            return prefix + "，命令发布失败";
        }
        if (Integer.valueOf(DeviceCommandStatus.TIMED_OUT).equals(status)) {
            return prefix + "，等待执行结果超时";
        }

        return prefix + "，命令状态未知";
    }
}