package com.plagod.service;

import com.plagod.dto.monitor.GeofenceCreateDTO;
import com.plagod.dto.monitor.GeofenceUpdateDTO;
import com.plagod.vo.monitor.*;

import java.time.LocalDateTime;

public interface GeofenceAdminService {

    GeofenceVO create(GeofenceCreateDTO dto);

    GeofenceVO update(Long fenceId, GeofenceUpdateDTO dto);

    GeofenceVO toggle(Long fenceId, Integer enabled);

    void delete(Long fenceId);

    GeofenceVO get(Long fenceId);

    GeofencePageResult page(long current, long size, Integer enabled, String keyword);

    GeofenceEventPageResult pageEvents(long current, long size, Long fenceId, Long userId, Long sessionId, String mac, String eventType, LocalDateTime startTime, LocalDateTime endTime);
}