package com.plagod.service;

import com.plagod.vo.monitor.AlertEventPageResult;
import com.plagod.vo.monitor.AlertEventVO;

import java.time.LocalDateTime;

public interface AlertEventService {

    AlertEventPageResult pageAlerts(long current, long size, Integer level, Integer status, String mac,
                                    LocalDateTime startTime, LocalDateTime endTime);

    AlertEventVO getAlert(Long id);

    void handle(Long id, Long handleUserId);
}
