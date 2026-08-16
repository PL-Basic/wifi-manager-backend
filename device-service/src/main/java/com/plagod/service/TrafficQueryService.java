package com.plagod.service;

import com.plagod.vo.device.TrafficPageResult;

import java.time.LocalDateTime;

public interface TrafficQueryService {
    TrafficPageResult pageTraffic(Long tenantId, long current, long size, String mac, Long sessionId, String dstIp, LocalDateTime startTime, LocalDateTime endTime);
    TrafficPageResult pageOwnedTraffic(Long tenantId, Long ownerUserId, long current, long size, String mac, Long sessionId, String dstIp, LocalDateTime startTime, LocalDateTime endTime);
}
