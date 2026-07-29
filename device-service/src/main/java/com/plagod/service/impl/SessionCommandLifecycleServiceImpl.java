package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.plagod.constant.DeviceCommandPurpose;
import com.plagod.constant.DeviceCommandStatus;
import com.plagod.constant.SessionStatus;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.entity.device.SessionRecord;
import com.plagod.mapper.DeviceCommandRecordMapper;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.service.PortalSessionService;
import com.plagod.service.SessionCommandLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class SessionCommandLifecycleServiceImpl implements SessionCommandLifecycleService {

    private static final String COMMAND_TYPE_ALLOW = "ALLOW";

    @Autowired
    private SessionRecordMapper sessionRecordMapper;

    @Autowired
    private DeviceCommandRecordMapper commandRecordMapper;

    @Autowired
    private PortalSessionService portalSessionService;


    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void handleTerminalCommand(DeviceCommandRecord command) {
        if (isForceLoginReplacementRevoke(command)) {
            handleForceLoginReplacement(command);
            return;
        }

        // 非 Session ALLOW 命令不能影响 Session。
        if (!isSessionAllowCommand(command)) {
            return;
        }

        if (command.getCommandId() == null || command.getSessionId() == null || command.getSessionId() <= 0) {
            throw new IllegalStateException("Session ALLOW 命令缺少关联标识");
        }

        if (!DeviceCommandStatus.isTerminal(command.getStatus())) {
            throw new IllegalStateException("非终态命令不能驱动 Session 状态");
        }

        // 锁顺序固定为：命令行锁 -> Session 行锁。
        SessionRecord session = sessionRecordMapper.selectByIdForUpdate(command.getSessionId());

        // 不根据命令反向创建 Session。
        if (session == null) {
            log.warn("Session ALLOW 命令关联的 Session 不存在，commandId={}, sessionId={}", command.getCommandId(), command.getSessionId());
            return;
        }

        Long latestCommandId =
                commandRecordMapper.selectLatestSessionAllowCommandId(command.getSessionId());

        if (latestCommandId == null) {
            throw new IllegalStateException("无法确定 Session 最新的 ALLOW 命令");
        }

        // 旧命令的迟到结果、迟到超时或者迟到失败不能覆盖新命令。
        if (!command.getCommandId().equals(latestCommandId)) {
            log.info("忽略非最新 ALLOW 对 Session 的状态驱动，commandId={}, latestCommandId={}, sessionId={}", command.getCommandId(), latestCommandId, command.getSessionId());
            return;
        }

        if (Integer.valueOf(DeviceCommandStatus.SUCCEEDED).equals(command.getStatus())) {
            activateSession(session, command);
            return;
        }

        closeSession(session, command);
    }

    private boolean isSessionAllowCommand(DeviceCommandRecord command) {

        return command != null && COMMAND_TYPE_ALLOW.equals(command.getCommandType()) && DeviceCommandPurpose.isSessionAllowPurpose(command.getPurpose());
    }

    private void activateSession(SessionRecord session, DeviceCommandRecord command) {

        // 已经关闭的 Session 不能被迟到的成功结果重新打开。
        if (!SessionStatus.isOpen(session.getStatus())) {
            log.info("忽略已关闭 Session 的 ALLOW 成功结果，sessionId={}, commandId={}", session.getSessionId(), command.getCommandId());
            return;
        }

        Integer ttlSeconds = command.getTtlSeconds();

        if (ttlSeconds == null || ttlSeconds < 1 || ttlSeconds > 86400) {
            throw new IllegalStateException("Session ALLOW 命令的 TTL 无效");
        }

        LocalDateTime confirmedAt = resolveTerminalTime(command);

        UpdateWrapper<SessionRecord> update = new UpdateWrapper<>();

        update.eq("session_id", session.getSessionId())
                .in("status",
                        SessionStatus.ACTIVE,
                        SessionStatus.PENDING)
                .set("status", SessionStatus.ACTIVE)
                .set("last_renew_time", confirmedAt)
                .set("expire_time",
                        confirmedAt.plusSeconds(ttlSeconds))
                .set("logout_time", null)
                .set("end_reason", null);

        // 首次确认后才开始计算使用时长。
        // ACTIVE 的重复认证和续租不能重置原有计费基线。
        if (SessionStatus.isPending(session.getStatus())) {
            update.set("last_billed_time", confirmedAt);
        }

        if (sessionRecordMapper.update(null, update) != 1) {
            throw new IllegalStateException("Session 激活状态保存失败");
        }

        log.info("Session ALLOW 已确认，sessionId={}, commandId={}", session.getSessionId(), command.getCommandId());
    }

    private void closeSession(SessionRecord session, DeviceCommandRecord command) {

        if (!SessionStatus.isOpen(session.getStatus())) {
            return;
        }

        LocalDateTime closedAt = resolveTerminalTime(command);
        String endReason = resolveFailureReason(command.getStatus());

        UpdateWrapper<SessionRecord> update = new UpdateWrapper<>();

        update.eq("session_id", session.getSessionId())
                .in("status",
                        SessionStatus.ACTIVE,
                        SessionStatus.PENDING)
                .set("status", SessionStatus.CLOSED)
                .set("expire_time", closedAt)
                .set("logout_time", closedAt)
                .set("end_reason", endReason);

        if (sessionRecordMapper.update(null, update) != 1) {
            throw new IllegalStateException("Session 失败关闭状态保存失败");
        }

        log.warn("Session ALLOW 未成功，Session 已关闭，sessionId={}, commandId={}, reason={}", session.getSessionId(), command.getCommandId(), endReason);
    }

    private LocalDateTime resolveTerminalTime(DeviceCommandRecord command) {

        return command.getResultTime() == null ? LocalDateTime.now() : command.getResultTime();
    }

    private String resolveFailureReason(Integer commandStatus) {
        if (Integer.valueOf(DeviceCommandStatus.EXECUTION_FAILED).equals(commandStatus)) {
            return "ALLOW_EXECUTION_FAILED";
        }

        if (Integer.valueOf(DeviceCommandStatus.PUBLISH_FAILED).equals(commandStatus)) {
            return "ALLOW_PUBLISH_FAILED";
        }

        if (Integer.valueOf(DeviceCommandStatus.TIMED_OUT).equals(commandStatus)) {
            return "ALLOW_RESULT_TIMEOUT";
        }

        throw new IllegalStateException("未知的 Session ALLOW 失败状态");
    }

    private boolean isForceLoginReplacementRevoke(DeviceCommandRecord command) {

        return command != null && "REVOKE_ACCESS".equals(command.getCommandType()) && DeviceCommandPurpose.FORCE_LOGIN_REPLACE.equals(command.getPurpose());
    }

    private void handleForceLoginReplacement(DeviceCommandRecord command) {

        if (command.getSessionId() == null || command.getSessionId() <= 0) {
            throw new IllegalStateException("强制替换撤销命令缺少旧 SessionId");
        }

        if (!DeviceCommandStatus.isTerminal(command.getStatus())) {
            throw new IllegalStateException("非终态撤销命令不能驱动强制替换");
        }

        if (Integer.valueOf(DeviceCommandStatus.SUCCEEDED).equals(command.getStatus())) {
            // 旧授权已由固件撤销，现在才允许生成新 ALLOW。
            portalSessionService.activateWaitingReplacement(command.getSessionId());
            return;
        }

        SessionRecord waiting = sessionRecordMapper.selectWaitingReplacementForUpdate(command.getSessionId());

        if (waiting == null) {
            return;
        }

        LocalDateTime now = command.getResultTime() == null ? LocalDateTime.now() : command.getResultTime();

        waiting.setStatus(SessionStatus.CLOSED);
        waiting.setExpireTime(now);
        waiting.setLogoutTime(now);
        waiting.setEndReason(resolveReplacementFailureReason(command.getStatus()));

        if (sessionRecordMapper.updateById(waiting) != 1) {
            throw new IllegalStateException("强制替换失败状态保存失败");
        }
    }

    private String resolveReplacementFailureReason(Integer commandStatus) {

        if (Integer.valueOf(DeviceCommandStatus.EXECUTION_FAILED).equals(commandStatus)) {
            return "FORCE_REVOKE_EXEC_FAILED";
        }

        if (Integer.valueOf(DeviceCommandStatus.PUBLISH_FAILED).equals(commandStatus)) {
            return "FORCE_REVOKE_PUBLISH_FAILED";
        }

        if (Integer.valueOf(DeviceCommandStatus.TIMED_OUT).equals(commandStatus)) {
            return "FORCE_REVOKE_RESULT_TIMEOUT";
        }

        throw new IllegalStateException("未知的强制替换撤销状态");
    }
}