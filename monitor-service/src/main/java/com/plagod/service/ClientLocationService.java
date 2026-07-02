package com.plagod.service;

import com.plagod.vo.monitor.ClientLocationPageResult;
import com.plagod.dto.ClientLocationReportDTO;

import java.time.LocalDateTime;

public interface ClientLocationService {

    Long report(ClientLocationReportDTO dto, Long userId);

    ClientLocationPageResult pageLocations(long current, long size, String mac, Long userId,
                                           LocalDateTime startTime, LocalDateTime endTime);
}
