package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.constant.SessionStatus;
import com.plagod.entity.device.Esp32Node;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.vo.device.LocationSessionContextVO;
import com.plagod.vo.device.SessionPageResult;
import com.plagod.vo.device.SessionRecordVO;
import com.plagod.entity.device.SessionRecord;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.service.SessionQueryService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class SessionQueryServiceImpl implements SessionQueryService {

    @Autowired
    private SessionRecordMapper sessionRecordMapper;

    @Autowired
    private Esp32NodeMapper esp32NodeMapper;

    @Value("${wifi.portal.session-offline-timeout-seconds:30}")
    private long sessionOfflineTimeoutSeconds;

    @Value("${wifi.device.heartbeat-timeout-seconds:60}")
    private long heartbeatTimeoutSeconds;

    @Override
    public SessionPageResult pageSessions(long current, long size, String mac, Long nodeId, Long userId, Integer status) {
        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<SessionRecord> queryWrapper = new QueryWrapper<>();
        if (StringUtils.hasText(mac)) {
            queryWrapper.like("mac", mac);
        }
        if (nodeId != null) {
            queryWrapper.eq("node_id", nodeId);
        }
        if (userId != null) {
            queryWrapper.eq("user_id", userId);
        }
        if (status != null) {
            queryWrapper.eq("status", status);
        }
        queryWrapper.orderByDesc("login_time");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SessionRecord> page = sessionRecordMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageCurrent, pageSize), queryWrapper);

        List<SessionRecordVO> records = new ArrayList<>();
        for (SessionRecord item : page.getRecords()) {
            SessionRecordVO vo = new SessionRecordVO();
            BeanUtils.copyProperties(item, vo);
            records.add(vo);
        }

        SessionPageResult result = new SessionPageResult();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }
    @Override
    @Transactional(readOnly = true)
    public LocationSessionContextVO getLocationContext(Long ownerUserId,
                                                       Long sessionId) {

        if (ownerUserId == null || ownerUserId <= 0 || sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException("缺少有效用户或 Session");
        }

        if (sessionOfflineTimeoutSeconds <= 0 || heartbeatTimeoutSeconds < 10 || heartbeatTimeoutSeconds > 3600) {
            throw new IllegalStateException("位置 Session 校验配置无效");
        }

        SessionRecord session = sessionRecordMapper.selectById(sessionId);

        if (session == null || !Objects.equals(ownerUserId, session.getUserId())) {

            // 不区分不存在和越权，避免枚举他人的 Session。
            throw ApiStatusException.notFound("Session 不存在或无权访问");
        }

        LocalDateTime now = LocalDateTime.now();

        if (!SessionStatus.isActive(session.getStatus()) || session.getExpireTime() == null || !session.getExpireTime().isAfter(now)) {

            throw ApiStatusException.conflict("Session 当前不可用于位置上报");
        }

        LocalDateTime sessionCutoff = now.minusSeconds(sessionOfflineTimeoutSeconds);

        if (session.getLastSeenTime() == null || !session.getLastSeenTime().isAfter(sessionCutoff)) {

            throw ApiStatusException.conflict("Session 已经离线");
        }

        Esp32Node node = esp32NodeMapper.selectById(session.getNodeId());
        LocalDateTime nodeCutoff = now.minusSeconds(heartbeatTimeoutSeconds);

        if (node == null || !Integer.valueOf(1).equals(node.getStatus()) || node.getLastHeartbeat() == null || !node.getLastHeartbeat().isAfter(nodeCutoff)) {

            throw ApiStatusException.conflict("Session 所属节点当前不可用");
        }

        LocationSessionContextVO result = new LocationSessionContextVO();

        result.setSessionId(session.getSessionId());
        result.setUserId(session.getUserId());
        result.setNodeId(session.getNodeId());
        result.setDeviceCode(node.getDeviceCode());
        result.setMac(session.getMac());
        result.setExpireTime(session.getExpireTime());
        result.setLastSeenTime(session.getLastSeenTime());
        result.setNodeLastHeartbeat(node.getLastHeartbeat());

        return result;
    }
}