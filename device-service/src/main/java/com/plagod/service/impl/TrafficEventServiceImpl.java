package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.dto.DeviceTrafficEvent;
import com.plagod.entity.SessionRecord;
import com.plagod.entity.TrafficLog;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.mapper.TrafficLogMapper;
import com.plagod.service.TrafficEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TrafficEventServiceImpl implements TrafficEventService {

    @Autowired
    private TrafficLogMapper trafficLogMapper;

    @Autowired
    private SessionRecordMapper sessionRecordMapper;

    @Override
    public void handleTrafficEvent(DeviceTrafficEvent event) {
        if (event == null || !StringUtils.hasText(event.getMac())) {
            throw new IllegalArgumentException("流量事件缺少 mac");
        }

        Long sessionId = event.getSessionId();
        if (sessionId == null) {
            QueryWrapper<SessionRecord> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("mac", event.getMac())
                    .eq("status", 1)
                    .orderByDesc("login_time")
                    .last("limit 1");
            SessionRecord sessionRecord = sessionRecordMapper.selectOne(queryWrapper);
            if (sessionRecord != null) {
                sessionId = sessionRecord.getSessionId();
            }
        }

        if (sessionId == null) {
            throw new IllegalArgumentException("未找到可关联的在线会话");
        }

        TrafficLog log = new TrafficLog();
        log.setSessionId(sessionId);
        log.setMac(event.getMac());
        log.setDstIp(event.getDstIp());
        log.setDstPort(event.getDstPort());
        log.setSni(event.getSni());
        log.setProtocol(event.getProtocol());
        log.setBytesUp(event.getBytesUp() == null ? 0L : event.getBytesUp());
        log.setBytesDown(event.getBytesDown() == null ? 0L : event.getBytesDown());
        trafficLogMapper.insert(log);

        QueryWrapper<SessionRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("session_id", sessionId);
        SessionRecord sessionRecord = sessionRecordMapper.selectOne(queryWrapper);
        if (sessionRecord != null) {
            sessionRecord.setBytesUp((sessionRecord.getBytesUp() == null ? 0L : sessionRecord.getBytesUp()) + log.getBytesUp());
            sessionRecord.setBytesDown((sessionRecord.getBytesDown() == null ? 0L : sessionRecord.getBytesDown()) + log.getBytesDown());
            sessionRecordMapper.updateById(sessionRecord);
        }
    }
}
