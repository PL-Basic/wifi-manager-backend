package com.plagod.service.impl;

import com.plagod.dto.ClientLocationReportDTO;
import com.plagod.entity.monitor.ClientLocation;
import com.plagod.entity.monitor.LocationAuthorization;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.ClientLocationMapper;
import com.plagod.mapper.LocationAuthorizationMapper;
import com.plagod.service.GeofenceEvaluationService;
import com.plagod.support.StableUnits;
import com.plagod.util.GeoMath;
import com.plagod.vo.device.LocationSessionContextVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class ClientLocationReportTransactionService {

    @Autowired
    private ClientLocationMapper clientLocationMapper;

    @Autowired
    private LocationAuthorizationMapper locationAuthorizationMapper;

    @Autowired
    private GeofenceEvaluationService geofenceEvaluationService;

    @Transactional(rollbackFor = Exception.class)
    public Long persist(Long tenantId,
                        LocationSessionContextVO context,
                        ClientLocationReportDTO dto,
                        long minimumReportIntervalSeconds,
                        double maximumSpeedMetersPerSecond) {
        Long userId = context.getUserId();
        Long sessionId = context.getSessionId();
        LocalDateTime now = LocalDateTime.now();

        locationAuthorizationMapper.ensureAuthorizationRowByTenant(
                tenantId,
                userId);
        LocationAuthorization authorization =
                locationAuthorizationMapper.selectByUserIdForUpdateAndTenant(
                        tenantId,
                        userId);

        if (authorization == null
                || !Integer.valueOf(1).equals(authorization.getEnabled())
                || authorization.getConsentTime() == null) {
            throw ApiStatusException.conflict("用户尚未开启位置共享");
        }

        validateReportInterval(
                authorization,
                now,
                minimumReportIntervalSeconds);
        ClientLocation previous =
                clientLocationMapper.selectLatestTrustedPointByTenant(
                        tenantId,
                        userId,
                        sessionId);
        validateLocationJump(
                previous,
                dto,
                now,
                maximumSpeedMetersPerSecond);

        ClientLocation entity = new ClientLocation();
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setNodeId(context.getNodeId());
        entity.setDeviceCode(context.getDeviceCode());
        entity.setMac(context.getMac());
        entity.setTrustedBinding(1);
        entity.setLatitude(dto.getLatitude());
        entity.setLongitude(dto.getLongitude());
        entity.setAccuracy(dto.getAccuracy());
        entity.setSource(normalizeSource(dto.getSource()));
        entity.setConsentTime(authorization.getConsentTime());
        entity.setReportTime(now);
        entity.setCreateTime(now);

        if (clientLocationMapper.insert(entity) != 1
                || entity.getId() == null) {
            throw new IllegalStateException("位置上报保存失败");
        }

        authorization.setLastReportTime(now);
        persistAuthorization(
                tenantId,
                authorization,
                "位置授权状态更新失败");
        geofenceEvaluationService.evaluate(previous, entity);
        return entity.getId();
    }

    private String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            return "browser";
        }
        return source.trim().toLowerCase(Locale.ROOT);
    }

    private void persistAuthorization(
            Long tenantId,
            LocationAuthorization authorization,
            String failureMessage) {
        Integer expectedVersion = authorization.getVersion();
        if (expectedVersion == null || expectedVersion < 0) {
            throw new IllegalStateException("位置授权版本无效");
        }

        int affected = locationAuthorizationMapper.updateByTenantAndVersion(
                tenantId,
                authorization.getUserId(),
                authorization.getEnabled(),
                authorization.getConsentTime(),
                authorization.getRevokedTime(),
                authorization.getLastReportTime(),
                expectedVersion);
        if (affected != 1) {
            throw ApiStatusException.conflict(failureMessage);
        }
        authorization.setVersion(expectedVersion + 1);
    }

    private void validateReportInterval(
            LocationAuthorization authorization,
            LocalDateTime now,
            long minimumReportIntervalSeconds) {
        LocalDateTime lastReportTime = authorization.getLastReportTime();
        if (lastReportTime == null) {
            return;
        }

        LocalDateTime nextAllowedTime =
                lastReportTime.plusSeconds(minimumReportIntervalSeconds);
        if (!now.isBefore(nextAllowedTime)) {
            return;
        }

        long remainingMillis =
                java.time.Duration.between(now, nextAllowedTime).toMillis();
        long retryAfterSeconds = Math.max(
                1L,
                (remainingMillis
                        + StableUnits.MILLISECONDS_PER_SECOND
                        - 1L)
                        / StableUnits.MILLISECONDS_PER_SECOND);
        throw ApiStatusException.tooManyRequests(
                "位置上报过于频繁，请稍后再试",
                retryAfterSeconds);
    }

    private void validateLocationJump(
            ClientLocation previous,
            ClientLocationReportDTO current,
            LocalDateTime now,
            double maximumSpeedMetersPerSecond) {
        if (previous == null) {
            return;
        }

        if (previous.getLatitude() == null
                || previous.getLongitude() == null
                || previous.getAccuracy() == null
                || previous.getReportTime() == null) {
            throw new IllegalStateException("最近可信位置数据不完整");
        }

        long elapsedMillis = java.time.Duration.between(
                previous.getReportTime(),
                now).toMillis();
        if (elapsedMillis <= 0) {
            throw new IllegalArgumentException("位置上报时间顺序异常");
        }

        double elapsedSeconds = elapsedMillis
                / (double) StableUnits.MILLISECONDS_PER_SECOND;
        double distanceMeters = GeoMath.distanceMeters(
                previous.getLatitude(),
                previous.getLongitude(),
                current.getLatitude(),
                current.getLongitude());

        // 两次定位精度作为误差缓冲，避免普通GPS漂移被当成异常移动。
        double allowedDistance = maximumSpeedMetersPerSecond * elapsedSeconds
                + previous.getAccuracy().doubleValue()
                + current.getAccuracy().doubleValue();
        if (distanceMeters > allowedDistance) {
            throw new IllegalArgumentException(
                    "位置变化明显异常，本次上报已拒绝");
        }
    }
}
