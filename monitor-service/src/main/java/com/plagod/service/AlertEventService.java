package com.plagod.service;

import com.plagod.security.TrustedRequestContext;
import com.plagod.vo.monitor.AlertEventPageResult;
import com.plagod.vo.monitor.AlertEventVO;

import java.time.LocalDateTime;

public interface AlertEventService {

    AlertEventPageResult pageAlerts(TrustedRequestContext context,
                                    long current, long size, Integer level, Integer status, String mac,
                                    LocalDateTime startTime, LocalDateTime endTime);

    AlertEventVO getAlert(TrustedRequestContext context, Long id);

    void handle(TrustedRequestContext context, Long id);
}
