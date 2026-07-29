package com.plagod.service;

import com.plagod.dto.ClientLocationReportDTO;
import com.plagod.vo.monitor.ClientLocationPageResult;
import com.plagod.vo.monitor.LocationAuthorizationVO;

import java.time.LocalDateTime;

public interface ClientLocationService {

    Long report(Long sessionId, ClientLocationReportDTO dto, Long userId);

    LocationAuthorizationVO getAuthorization(Long userId);

    LocationAuthorizationVO grantAuthorization(Long userId);

    LocationAuthorizationVO revokeAuthorization(Long userId);

    long clearOwnedHistory(Long userId);

    ClientLocationPageResult pageLocations(long current, long size, String mac, Long userId, LocalDateTime startTime, LocalDateTime endTime);

    ClientLocationPageResult pageOwnedLocations(Long ownerUserId, long current, long size, String mac, LocalDateTime startTime, LocalDateTime endTime);
}