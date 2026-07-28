package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.audit.Audited;
import com.plagod.constant.DeviceCommandPurpose;
import com.plagod.constant.SessionStatus;
import com.plagod.dto.device.MacBlacklistCreateDTO;
import com.plagod.entity.Esp32Node;
import com.plagod.entity.MacBlacklist;
import com.plagod.entity.SessionRecord;
import com.plagod.mapper.ClientAccessGuardMapper;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.mapper.MacBlacklistMapper;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.service.DeviceCommandService;
import com.plagod.service.MacBlacklistService;
import com.plagod.service.SessionLeaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class MacBlacklistServiceImpl implements MacBlacklistService {

    private static final String END_REASON = "BLACKLISTED";
    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");

    @Autowired
    private ClientAccessGuardMapper clientAccessGuardMapper;
    @Autowired
    private MacBlacklistMapper macBlacklistMapper;
    @Autowired
    private SessionRecordMapper sessionRecordMapper;
    @Autowired
    private Esp32NodeMapper esp32NodeMapper;
    @Autowired
    private SessionLeaseService sessionLeaseService;
    @Autowired
    private DeviceCommandService deviceCommandService;

    @Override
    @Audited(action = "blacklist.add")
    @Transactional(rollbackFor = Exception.class)
    public void addBlacklist(MacBlacklistCreateDTO createDTO) {
        if (createDTO == null) {
            throw new IllegalArgumentException("黑名单参数不能为空");
        }

        String mac = normalizeMac(createDTO.getMac());
        if (mac == null) {
            throw new IllegalArgumentException("MAC 地址格式不正确");
        }

        LocalDateTime now = LocalDateTime.now();
        if (createDTO.getExpireTime() != null && !createDTO.getExpireTime().isAfter(now)) {
            throw new IllegalArgumentException("黑名单过期时间必须晚于当前时间");
        }

        String reason = cleanReason(createDTO.getReason());
        lockClientAccess(mac);

        QueryWrapper<MacBlacklist> existingQuery = new QueryWrapper<>();
        existingQuery.eq("mac", mac);
        if (macBlacklistMapper.selectCount(existingQuery) > 0) {
            throw new IllegalArgumentException("该 MAC 已存在黑名单记录");
        }

        MacBlacklist blacklist = new MacBlacklist();
        blacklist.setMac(mac);
        blacklist.setReason(reason);
        blacklist.setOperatorId(createDTO.getOperatorId());
        blacklist.setExpireTime(createDTO.getExpireTime());

        if (macBlacklistMapper.insert(blacklist) != 1) {
            throw new IllegalStateException("黑名单新增失败");
        }

        closeAllocatedSessions(mac, now);
    }

    private void closeAllocatedSessions(String mac, LocalDateTime now) {
        List<SessionRecord> sessions = sessionRecordMapper.selectAllocatedByMacForUpdate(mac);

        for (SessionRecord session : sessions) {
            boolean waitingReplacement = SessionStatus.isWaitingReplacement(session.getStatus());

            // 只有固件已经确认放行的 ACTIVE Session 需要最终结算。
            if (SessionStatus.isActive(session.getStatus())) {
                sessionLeaseService.settleFinalUsage(session, now);
            }

            session.setStatus(SessionStatus.CLOSED);
            session.setExpireTime(now);
            session.setLogoutTime(now);
            session.setEndReason(END_REASON);

            if (sessionRecordMapper.updateById(session) != 1) {
                throw new IllegalStateException("黑名单关联 Session 关闭失败，sessionId=" + session.getSessionId());
            }

            // WAITING_REPLACEMENT 从未收到 ALLOW，不需要固件撤销。
            if (waitingReplacement) {
                continue;
            }

            Esp32Node node = esp32NodeMapper.selectByNodeIdIncludeDeleted(session.getNodeId());

            if (node == null || !StringUtils.hasText(node.getDeviceCode())) {
                throw new IllegalStateException("Session 关联的 ESP32 节点不存在，sessionId=" + session.getSessionId());
            }

            deviceCommandService.revokeClientAccess(session.getNodeId(), node.getDeviceCode(), session.getMac(), session.getSessionId(), DeviceCommandPurpose.BLACKLIST_REVOKE);
        }
    }

    private void lockClientAccess(String mac) {

        clientAccessGuardMapper.ensureGuardRow(mac);
        String lockedMac = clientAccessGuardMapper.selectMacForUpdate(mac);

        if (!mac.equals(lockedMac)) {
            throw new IllegalStateException("客户端访问状态锁定失败");
        }
    }

    private String normalizeMac(String mac) {
        if (!StringUtils.hasText(mac)) {
            return null;
        }

        String normalized = mac.trim().toUpperCase(Locale.ROOT);
        return MAC_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private String cleanReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }

        String cleaned = reason.trim();
        if (cleaned.length() > 255) {
            throw new IllegalArgumentException("黑名单原因长度不能超过 255");
        }
        return cleaned;
    }
}