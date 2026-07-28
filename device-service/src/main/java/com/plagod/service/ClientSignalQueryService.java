package com.plagod.service;

import com.plagod.vo.device.ClientSignalPageResult;

import java.time.LocalDateTime;

public interface ClientSignalQueryService {

    ClientSignalPageResult pageClientSignals(long current, long size, String deviceCode, Long nodeId, String mac, Long sessionId, String state, LocalDateTime startTime, LocalDateTime endTime);

    ClientSignalPageResult pageOwnedClientSignals(Long ownerUserId, long current, long size, String deviceCode, Long nodeId, String mac, Long sessionId, String state, LocalDateTime startTime, LocalDateTime endTime);

    boolean wasRecentlyObserved(Long nodeId, String deviceCode, String mac, LocalDateTime sinceTime);
}