package com.plagod.service;

import com.plagod.dto.ClientLocationReportDTO;
import com.plagod.security.TrustedRequestContext;
import com.plagod.vo.monitor.ClientLocationPageResult;
import com.plagod.vo.monitor.LocationAuthorizationVO;

import java.time.LocalDateTime;

public interface ClientLocationService {

    Long report(TrustedRequestContext context,
                Long sessionId,
                ClientLocationReportDTO dto);

    LocationAuthorizationVO getAuthorization(TrustedRequestContext context);

    LocationAuthorizationVO grantAuthorization(TrustedRequestContext context);

    LocationAuthorizationVO revokeAuthorization(TrustedRequestContext context);

    long clearOwnedHistory(TrustedRequestContext context);

    ClientLocationPageResult pageLocations(
            TrustedRequestContext context,
            long current,
            long size,
            String mac,
            Long userId,
            LocalDateTime startTime,
            LocalDateTime endTime);

    ClientLocationPageResult pageOwnedLocations(
            TrustedRequestContext context,
            long current,
            long size,
            String mac,
            LocalDateTime startTime,
            LocalDateTime endTime);
}
