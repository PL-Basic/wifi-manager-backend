package com.plagod.service;

import com.plagod.vo.monitor.AuditLogPageResult;
import com.plagod.vo.monitor.AuditLogVO;

import java.time.LocalDateTime;

public interface AuditLogQueryService {

    AuditLogPageResult pageAudits(long current, long size, String action, String operatorName, String target,
                                  LocalDateTime startTime, LocalDateTime endTime);

    AuditLogVO getAudit(Long id);
}
