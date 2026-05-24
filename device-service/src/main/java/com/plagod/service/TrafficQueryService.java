package com.plagod.service;

import com.plagod.dto.TrafficPageResult;

import java.time.LocalDateTime;

public interface TrafficQueryService {
    TrafficPageResult pageTraffic(long current, long size, String mac, Long sessionId, String dstIp,
                                  LocalDateTime startTime, LocalDateTime endTime);
}
