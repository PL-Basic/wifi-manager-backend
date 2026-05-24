package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.dto.DeviceTrafficEvent;
import com.plagod.entity.SessionRecord;
import com.plagod.entity.TrafficLog;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.mapper.TrafficLogMapper;
import com.plagod.service.TrafficEventService;
import com.plagod.service.TrafficRuleEvaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TrafficEventServiceImpl implements TrafficEventService {

    @Autowired
    private TrafficLogMapper trafficLogMapper;

    @Autowired
    private SessionRecordMapper sessionRecordMapper;

    @Autowired
    private TrafficRuleEvaluator trafficRuleEvaluator;

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

        TrafficLog trafficLog = new TrafficLog();
        trafficLog.setSessionId(sessionId);
        trafficLog.setMac(event.getMac());
        trafficLog.setDstIp(event.getDstIp());
        trafficLog.setDstPort(event.getDstPort());
        trafficLog.setSni(event.getSni());
        trafficLog.setProtocol(event.getProtocol());
        trafficLog.setBytesUp(event.getBytesUp() == null ? 0L : event.getBytesUp());
        trafficLog.setBytesDown(event.getBytesDown() == null ? 0L : event.getBytesDown());
        trafficLogMapper.insert(trafficLog);

        QueryWrapper<SessionRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("session_id", sessionId);
        SessionRecord sessionRecord = sessionRecordMapper.selectOne(queryWrapper);
        if (sessionRecord != null) {
            sessionRecord.setBytesUp((sessionRecord.getBytesUp() == null ? 0L : sessionRecord.getBytesUp()) + trafficLog.getBytesUp());
            sessionRecord.setBytesDown((sessionRecord.getBytesDown() == null ? 0L : sessionRecord.getBytesDown()) + trafficLog.getBytesDown());
            sessionRecordMapper.updateById(sessionRecord);
        }

        // 评估 + 动作派发都丢线程池：monitor 卡住不能反压 MQTT 消费
        trafficRuleEvaluator.evaluateAndAct(event, sessionId, sessionRecord);
    }
}
