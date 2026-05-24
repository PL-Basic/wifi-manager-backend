package com.plagod.service;

import com.plagod.dto.AlertEventPageResult;

import java.time.LocalDateTime;

public interface AlertEventService {

    AlertEventPageResult pageAlerts(long current, long size, Integer level, Integer status, String mac,
                                    LocalDateTime startTime, LocalDateTime endTime);

    void handle(Long id, Long handleUserId);
}
