package com.plagod.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.entity.SessionRecord;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.service.SessionLeaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "wifi.portal", name = "lease-scheduler-enabled", havingValue = "true", matchIfMissing = false)
public class SessionLeaseScheduler {
    @Autowired
    private SessionRecordMapper sessionRecordMapper;
    @Autowired
    private SessionLeaseService sessionLeaseService;

    @Scheduled(fixedDelayString = "${wifi.portal.lease-scan-interval-ms:10000}",
            initialDelayString = "${wifi.portal.lease-initial-delay-ms:10000}")
    public void processActiveSessions() {
        // 扫描器只查询 SessionID,不持有统一的大事务
        List<SessionRecord> sessions = sessionRecordMapper.selectList(
                new QueryWrapper<SessionRecord>()
                        .select("session_id")
                        .eq("status", 1)
                        .orderByAsc("session_id")
        );

        for (SessionRecord session : sessions) {
            Long sessionId = session.getSessionId();
            if (sessionId == null) {
                continue;
            }
            try {
                // 调用独立 Spring Bean，使 processSession 的事务代理生效。
                sessionLeaseService.processSession(sessionId);
            } catch (Exception exception) {
                // 单个 Session 失败不能阻止其他 Session 继续处理。
                log.error("Session 续租处理失败，sessionId={}", sessionId, exception);
            }
        }
    }
}