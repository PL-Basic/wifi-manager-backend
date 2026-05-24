package com.plagod.service;

import com.plagod.dto.AuditLogPageResult;

import java.time.LocalDateTime;

public interface AuditLogQueryService {

    AuditLogPageResult pageAudits(long current, long size, String action, String operatorName, String target,
                                  LocalDateTime startTime, LocalDateTime endTime);
}
