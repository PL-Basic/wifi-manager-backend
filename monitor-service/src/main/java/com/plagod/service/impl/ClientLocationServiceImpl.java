package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.audit.Audited;
import com.plagod.client.DeviceLocationSessionClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.ClientLocationReportDTO;
import com.plagod.entity.monitor.ClientLocation;
import com.plagod.entity.monitor.LocationAuthorization;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.ClientLocationMapper;
import com.plagod.mapper.LocationAuthorizationMapper;
import com.plagod.security.MonitorTenantScope;
import com.plagod.security.TrustedRequestContext;
import com.plagod.service.ClientLocationService;
import com.plagod.support.PageBounds;
import com.plagod.support.StableUnits;
import com.plagod.service.GeofenceEvaluationService;
import com.plagod.util.GeoMath;
import com.plagod.vo.device.LocationSessionContextVO;
import com.plagod.vo.monitor.ClientLocationPageResult;
import com.plagod.vo.monitor.ClientLocationVO;
import com.plagod.vo.monitor.LocationAuthorizationVO;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ClientLocationServiceImpl implements ClientLocationService {

    private static final Logger log = LoggerFactory.getLogger(ClientLocationServiceImpl.class);

    @Autowired
    private ClientLocationMapper clientLocationMapper;
    @Autowired
    private LocationAuthorizationMapper locationAuthorizationMapper;
    @Autowired
    private DeviceLocationSessionClient deviceLocationSessionClient;
    @Autowired
    private GeofenceEvaluationService geofenceEvaluationService;
    @Autowired
    private MonitorTenantScope tenantScope;

    @Value("${wifi.location.minimum-report-interval-seconds:3}")
    private long minimumReportIntervalSeconds;
    @Value("${wifi.location.maximum-speed-meters-per-second:100}")
    private double maximumSpeedMetersPerSecond;



    @Override
    @Audited(
            action = "location.report",
            scope = Audited.Scope.TENANT,
            tenantIdSource = Audited.TenantIdSource.REQUEST,
            includeArgs = false)
    @Transactional(rollbackFor = Exception.class)
    public Long report(TrustedRequestContext trustedContext,
                       Long sessionId,
                       ClientLocationReportDTO dto) {
        Long tenantId = tenantScope.requireTenantId(trustedContext);
        Long userId = trustedContext.getUserId();
        validateIdentity(userId, sessionId);
        validateLocationPolicyConfiguration();

        LocationSessionContextVO context = resolveContext(
                tenantId,
                userId,
                sessionId);
        LocalDateTime now = LocalDateTime.now();

        locationAuthorizationMapper.ensureAuthorizationRowByTenant(
                tenantId,
                userId);
        LocationAuthorization authorization =
                locationAuthorizationMapper.selectByUserIdForUpdateAndTenant(
                        tenantId,
                        userId);

        if (authorization == null || !Integer.valueOf(1).equals(authorization.getEnabled()) || authorization.getConsentTime() == null) {

            throw ApiStatusException.conflict("用户尚未开启位置共享");
        }

        validateReportInterval(authorization, now);
        ClientLocation previous =
                clientLocationMapper.selectLatestTrustedPointByTenant(
                        tenantId,
                        userId,
                        sessionId);
        validateLocationJump(previous, dto, now);

        ClientLocation entity = new ClientLocation();
        entity.setTenantId(tenantId);
        entity.setUserId(context.getUserId());
        entity.setSessionId(context.getSessionId());
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

        if (clientLocationMapper.insert(entity) != 1 || entity.getId() == null) {
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

    @Override
    public LocationAuthorizationVO getAuthorization(
            TrustedRequestContext context) {
        Long tenantId = tenantScope.requireTenantId(context);
        Long userId = context.getUserId();
        return toAuthorizationVO(
                userId,
                locationAuthorizationMapper.selectByUserIdAndTenant(
                        tenantId,
                        userId));
    }

    @Override
    @Audited(
            action = "location.consent.grant",
            scope = Audited.Scope.TENANT,
            tenantIdSource = Audited.TenantIdSource.REQUEST,
            includeArgs = false)
    @Transactional(rollbackFor = Exception.class)
    public LocationAuthorizationVO grantAuthorization(
            TrustedRequestContext context) {
        Long tenantId = tenantScope.requireTenantId(context);
        Long userId = context.getUserId();

        locationAuthorizationMapper.ensureAuthorizationRowByTenant(
                tenantId,
                userId);
        LocationAuthorization authorization =
                locationAuthorizationMapper.selectByUserIdForUpdateAndTenant(
                        tenantId,
                        userId);

        if (authorization == null) {
            throw new IllegalStateException("位置授权记录初始化失败");
        }

        if (!Integer.valueOf(1).equals(authorization.getEnabled()) || authorization.getConsentTime() == null) {

            LocalDateTime now = LocalDateTime.now();

            authorization.setEnabled(1);
            authorization.setConsentTime(now);
            authorization.setRevokedTime(null);

            // 新授权周期不继承上一个周期的频率限制时间。
            authorization.setLastReportTime(null);
            persistAuthorization(
                    tenantId,
                    authorization,
                    "位置授权保存失败");
        }
        return toAuthorizationVO(userId, authorization);
    }

    @Override
    @Audited(
            action = "location.consent.revoke",
            scope = Audited.Scope.TENANT,
            tenantIdSource = Audited.TenantIdSource.REQUEST,
            includeArgs = false)
    @Transactional(rollbackFor = Exception.class)
    public LocationAuthorizationVO revokeAuthorization(
            TrustedRequestContext context) {
        Long tenantId = tenantScope.requireTenantId(context);
        Long userId = context.getUserId();

        locationAuthorizationMapper.ensureAuthorizationRowByTenant(
                tenantId,
                userId);
        LocationAuthorization authorization =
                locationAuthorizationMapper.selectByUserIdForUpdateAndTenant(
                        tenantId,
                        userId);

        if (authorization == null) {
            throw new IllegalStateException("位置授权记录初始化失败");
        }

        if (!Integer.valueOf(0).equals(authorization.getEnabled()) || authorization.getRevokedTime() == null) {
            LocalDateTime now = LocalDateTime.now();
            authorization.setEnabled(0);
            authorization.setRevokedTime(now);
            persistAuthorization(
                    tenantId,
                    authorization,
                    "位置授权撤销失败");
        }

        return toAuthorizationVO(userId, authorization);
    }

    @Override
    @Audited(
            action = "location.history.clear",
            scope = Audited.Scope.TENANT,
            tenantIdSource = Audited.TenantIdSource.REQUEST,
            includeArgs = false)
    @Transactional(rollbackFor = Exception.class)
    public long clearOwnedHistory(TrustedRequestContext context) {
        Long tenantId = tenantScope.requireTenantId(context);
        Long userId = context.getUserId();

        locationAuthorizationMapper.ensureAuthorizationRowByTenant(
                tenantId,
                userId);

        if (locationAuthorizationMapper
                .selectByUserIdForUpdateAndTenant(tenantId, userId)
                == null) {
            throw new IllegalStateException("位置授权记录初始化失败");
        }

        QueryWrapper<ClientLocation> deleteQuery = new QueryWrapper<>();
        deleteQuery.eq("tenant_id", tenantId);
        deleteQuery.eq("user_id", userId);
        geofenceEvaluationService.clearUserData(tenantId, userId);
        return clientLocationMapper.delete(deleteQuery);
    }

    @Override
    public ClientLocationPageResult pageLocations(
            TrustedRequestContext context,
            long current,
            long size,
            String mac,
            Long userId,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        return page(
                tenantScope.requireTenantId(context),
                current,
                size,
                mac,
                userId,
                startTime,
                endTime);
    }

    @Override
    public ClientLocationPageResult pageOwnedLocations(
            TrustedRequestContext context,
            long current,
            long size,
            String mac,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        Long tenantId = tenantScope.requireTenantId(context);
        return page(
                tenantId,
                current,
                size,
                mac,
                context.getUserId(),
                startTime,
                endTime);
    }

    private ClientLocationPageResult page(
            Long tenantId,
            long current,
            long size,
            String mac,
            Long userId,
            LocalDateTime startTime,
            LocalDateTime endTime) {

        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {

            throw new IllegalArgumentException("结束时间不能早于开始时间");
        }

        PageBounds pageBounds = PageBounds.of(
                current <= 0L
                        ? null
                        : (int) Math.min(current, Integer.MAX_VALUE),
                size <= 0L
                        ? null
                        : (int) Math.min(size, Integer.MAX_VALUE));

        QueryWrapper<ClientLocation> query = new QueryWrapper<>();
        query.eq("tenant_id", tenantId);

        if (StringUtils.hasText(mac)) {
            query.like("mac", mac.trim());
        }
        if (userId != null) {
            query.eq("user_id", userId);
        }
        if (startTime != null) {
            query.ge("report_time", startTime);
        }
        if (endTime != null) {
            query.le("report_time", endTime);
        }
        query.orderByDesc("report_time")
                .orderByDesc("id");

        Page<ClientLocation> resultPage = clientLocationMapper.selectPage(
                new Page<>(
                        pageBounds.getCurrent(),
                        pageBounds.getSize()),
                query);

        List<ClientLocationVO> records = new ArrayList<>();

        for (ClientLocation item : resultPage.getRecords()) {
            ClientLocationVO vo = new ClientLocationVO();
            BeanUtils.copyProperties(item, vo);
            records.add(vo);
        }

        ClientLocationPageResult result = new ClientLocationPageResult();
        result.setTotal(resultPage.getTotal());
        result.setCurrent(resultPage.getCurrent());
        result.setSize(resultPage.getSize());
        result.setRecords(records);

        return result;
    }

    private LocationSessionContextVO resolveContext(
            Long tenantId,
            Long userId,
            Long sessionId) {

        ApiResponse<LocationSessionContextVO> response;

        try {
            response = deviceLocationSessionClient.getLocationContext(
                    sessionId);
        } catch (FeignException exception) {
            log.warn("位置 Session 上下文调用失败，sessionId={}，status={}", sessionId, exception.status());

            throw mapLocationSessionFailure(exception.status());
        }

        if (response == null) {
            throw ApiStatusException.serviceUnavailable("设备服务暂时没有返回结果");
        }

        if (response.getCode() != 200) {
            throw mapLocationSessionFailure(response.getCode());
        }

        LocationSessionContextVO context = response.getData();

        if (context == null) {
            throw ApiStatusException.badGateway("设备服务未返回有效 Session 关系");
        }

        if (!Objects.equals(userId, context.getUserId())
                || !Objects.equals(sessionId, context.getSessionId())
                || context.getNodeId() == null
                || !StringUtils.hasText(context.getDeviceCode())
                || !StringUtils.hasText(context.getMac())) {

            throw ApiStatusException.badGateway("设备服务返回的 Session 关系不完整");
        }

        if (context.getTenantId() == null
                || context.getTenantId() <= 0
                || !Objects.equals(tenantId, context.getTenantId())) {
            throw ApiStatusException.badGateway(
                    "设备服务返回的 Session 租户归属不一致");
        }

        return context;
    }

    private ApiStatusException mapLocationSessionFailure(int status) {

        if (status == 404) {
            return ApiStatusException.notFound("Session 不存在或无权访问");
        }

        if (status == 409) {
            return ApiStatusException.conflict("Session 当前不可用于位置上报");
        }

        if (status == 429) {
            return ApiStatusException.tooManyRequests("位置 Session 校验请求过于频繁", 1L);
        }

        if (status == 502) {
            return ApiStatusException.badGateway("设备服务返回了无效响应");
        }

        // 401 通常代表内部 Token 配置不一致，不能把它暴露成用户未登录。
        return ApiStatusException.serviceUnavailable("设备服务暂时不可用");
    }

    private void validateIdentity(Long userId, Long sessionId) {
        validateUserId(userId);

        if (sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException("缺少有效 Session");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("缺少有效用户身份");
        }
    }

    private String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            return "browser";
        }
        return source.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private LocationAuthorizationVO toAuthorizationVO(Long userId, LocationAuthorization authorization) {

        LocationAuthorizationVO vo = new LocationAuthorizationVO();
        vo.setUserId(userId);

        if (authorization == null) {
            vo.setEnabled(0);
            return vo;
        }

        vo.setEnabled(authorization.getEnabled());
        vo.setConsentTime(authorization.getConsentTime());
        vo.setRevokedTime(authorization.getRevokedTime());
        vo.setLastReportTime(authorization.getLastReportTime());
        return vo;
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

    private void validateLocationPolicyConfiguration() {
        if (minimumReportIntervalSeconds < 1 || minimumReportIntervalSeconds > 3600) {
            throw new IllegalStateException("位置上报最小间隔配置无效");
        }

        if (!Double.isFinite(maximumSpeedMetersPerSecond) || maximumSpeedMetersPerSecond <= 0 || maximumSpeedMetersPerSecond > 1000) {
            throw new IllegalStateException("位置异常跳点速度配置无效");
        }
    }

    private void validateReportInterval(LocationAuthorization authorization, LocalDateTime now) {

        LocalDateTime lastReportTime = authorization.getLastReportTime();

        if (lastReportTime == null) {
            return;
        }

        LocalDateTime nextAllowedTime = lastReportTime.plusSeconds(minimumReportIntervalSeconds);

        if (!now.isBefore(nextAllowedTime)) {
            return;
        }

        long remainingMillis = java.time.Duration.between(now, nextAllowedTime).toMillis();

        long retryAfterSeconds = Math.max(
                1L,
                (remainingMillis
                        + StableUnits.MILLISECONDS_PER_SECOND
                        - 1L)
                        / StableUnits.MILLISECONDS_PER_SECOND);

        throw ApiStatusException.tooManyRequests("位置上报过于频繁，请稍后再试", retryAfterSeconds);
    }

    private void validateLocationJump(ClientLocation previous, ClientLocationReportDTO current, LocalDateTime now) {

        if (previous == null) {
            return;
        }

        if (previous.getLatitude() == null || previous.getLongitude() == null || previous.getAccuracy() == null || previous.getReportTime() == null) {
            throw new IllegalStateException("最近可信位置数据不完整");
        }

        long elapsedMillis = java.time.Duration.between(previous.getReportTime(), now).toMillis();

        if (elapsedMillis <= 0) {
            throw new IllegalArgumentException("位置上报时间顺序异常");
        }

        double elapsedSeconds = elapsedMillis
                / (double) StableUnits.MILLISECONDS_PER_SECOND;

        double distanceMeters = GeoMath.distanceMeters(previous.getLatitude(), previous.getLongitude(), current.getLatitude(), current.getLongitude());

        // 两次定位精度作为误差缓冲，避免普通GPS漂移被当成异常移动。
        double allowedDistance = maximumSpeedMetersPerSecond * elapsedSeconds + previous.getAccuracy().doubleValue() + current.getAccuracy().doubleValue();

        if (distanceMeters > allowedDistance) {
            throw new IllegalArgumentException("位置变化明显异常，本次上报已拒绝");
        }
    }


}
