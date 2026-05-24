package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.client.MonitorServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.DeviceTrafficEvent;
import com.plagod.dto.TrafficEvaluationRequest;
import com.plagod.dto.TrafficEvaluationResult;
import com.plagod.entity.SessionRecord;
import com.plagod.entity.TrafficLog;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.mapper.TrafficLogMapper;
import com.plagod.service.TrafficEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TrafficEventServiceImpl implements TrafficEventService {

    private static final Logger log = LoggerFactory.getLogger(TrafficEventServiceImpl.class);

    @Autowired
    private TrafficLogMapper trafficLogMapper;

    @Autowired
    private SessionRecordMapper sessionRecordMapper;

    @Autowired
    private MonitorServiceClient monitorServiceClient;

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

        evaluateAgainstRules(event, sessionId, sessionRecord);
    }

    private void evaluateAgainstRules(DeviceTrafficEvent event, Long sessionId, SessionRecord sessionRecord) {
        TrafficEvaluationRequest request = new TrafficEvaluationRequest();
        request.setMac(event.getMac());
        request.setSessionId(sessionId);
        request.setUserId(sessionRecord == null ? null : sessionRecord.getUserId());
        request.setDstIp(event.getDstIp());
        request.setDstPort(event.getDstPort());
        request.setSni(event.getSni());
        request.setProtocol(event.getProtocol());

        try {
            ApiResponse<TrafficEvaluationResult> response = monitorServiceClient.evaluate(request);
            if (response == null || response.getData() == null) {
                return;
            }
            TrafficEvaluationResult result = response.getData();
            if (result.isHit()) {
                log.info("traffic event {} matched {} rule(s), alertId={}",
                        event.getMac(), result.getHits() == null ? 0 : result.getHits().size(), result.getAlertId());
            }
        } catch (Exception ex) {
            log.warn("monitor evaluate failed for mac={} dstIp={}: {}", event.getMac(), event.getDstIp(), ex.getMessage());
        }
    }
}
