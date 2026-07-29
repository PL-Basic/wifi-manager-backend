package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.audit.Audited;
import com.plagod.client.DeviceLocationSessionClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.ClientLocationReportDTO;
import com.plagod.entity.ClientLocation;
import com.plagod.entity.LocationAuthorization;
import com.plagod.mapper.ClientLocationMapper;
import com.plagod.mapper.LocationAuthorizationMapper;
import com.plagod.service.ClientLocationService;
import com.plagod.vo.device.LocationSessionContextVO;
import com.plagod.vo.monitor.ClientLocationPageResult;
import com.plagod.vo.monitor.ClientLocationVO;
import com.plagod.vo.monitor.LocationAuthorizationVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClientLocationServiceImpl implements ClientLocationService {

    @Autowired
    private ClientLocationMapper clientLocationMapper;
    @Autowired
    private LocationAuthorizationMapper locationAuthorizationMapper;
    @Autowired
    private DeviceLocationSessionClient deviceLocationSessionClient;

    @Value("${wifi.internal.token:}")
    private String internalToken;
    @Value("${wifi.location.minimum-report-interval-seconds:3}")
    private long minimumReportIntervalSeconds;
    @Value("${wifi.location.maximum-speed-meters-per-second:100}")
    private double maximumSpeedMetersPerSecond;

    @Override
    @Audited(action = "location.report", includeArgs = false)
    @Transactional(rollbackFor = Exception.class)
    public Long report(Long sessionId, ClientLocationReportDTO dto, Long userId) {
        validateIdentity(userId, sessionId);
        validateLocationPolicyConfiguration();

        LocationSessionContextVO context = resolveContext(userId, sessionId);
        LocalDateTime now = LocalDateTime.now();

        locationAuthorizationMapper.ensureAuthorizationRow(userId);
        LocationAuthorization authorization = locationAuthorizationMapper.selectByUserIdForUpdate(userId);

        if (authorization == null || !Integer.valueOf(1).equals(authorization.getEnabled()) || authorization.getConsentTime() == null) {
            throw new IllegalStateException("用户尚未开启位置共享");
        }

        validateReportInterval(authorization, now);
        ClientLocation previous = clientLocationMapper.selectLatestTrustedPoint(userId, sessionId);
        validateLocationJump(previous, dto, now);

        ClientLocation entity = new ClientLocation();
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
        authorization.setUpdateTime(now);

        if (locationAuthorizationMapper.updateById(authorization) != 1) {
            throw new IllegalStateException("位置授权状态更新失败");
        }

        return entity.getId();
    }

    @Override
    public LocationAuthorizationVO getAuthorization(Long userId) {
        validateUserId(userId);
        return toAuthorizationVO(userId, locationAuthorizationMapper.selectById(userId));
    }

    @Override
    @Audited(action = "location.consent.grant", includeArgs = false)
    @Transactional(rollbackFor = Exception.class)
    public LocationAuthorizationVO grantAuthorization(Long userId) {
        validateUserId(userId);

        locationAuthorizationMapper.ensureAuthorizationRow(userId);
        LocationAuthorization authorization = locationAuthorizationMapper.selectByUserIdForUpdate(userId);

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
            authorization.setUpdateTime(now);

            if (locationAuthorizationMapper.updateById(authorization) != 1) {
                throw new IllegalStateException("位置授权保存失败");
            }
        }
        return toAuthorizationVO(userId, authorization);
    }

    @Override
    @Audited(action = "location.consent.revoke", includeArgs = false)
    @Transactional(rollbackFor = Exception.class)
    public LocationAuthorizationVO revokeAuthorization(Long userId) {
        validateUserId(userId);

        locationAuthorizationMapper.ensureAuthorizationRow(userId);
        LocationAuthorization authorization = locationAuthorizationMapper.selectByUserIdForUpdate(userId);

        if (authorization == null) {
            throw new IllegalStateException("位置授权记录初始化失败");
        }

        if (!Integer.valueOf(0).equals(authorization.getEnabled()) || authorization.getRevokedTime() == null) {
            LocalDateTime now = LocalDateTime.now();
            authorization.setEnabled(0);
            authorization.setRevokedTime(now);
            authorization.setUpdateTime(now);

            if (locationAuthorizationMapper.updateById(authorization) != 1) {
                throw new IllegalStateException("位置授权撤销失败");
            }
        }

        return toAuthorizationVO(userId, authorization);
    }

    @Override
    @Audited(action = "location.history.clear", includeArgs = false)
    @Transactional(rollbackFor = Exception.class)
    public long clearOwnedHistory(Long userId) {
        validateUserId(userId);

        locationAuthorizationMapper.ensureAuthorizationRow(userId);

        if (locationAuthorizationMapper.selectByUserIdForUpdate(userId) == null) {
            throw new IllegalStateException("位置授权记录初始化失败");
        }

        QueryWrapper<ClientLocation> deleteQuery = new QueryWrapper<>();
        deleteQuery.eq("user_id", userId);
        return clientLocationMapper.delete(deleteQuery);
    }

    @Override
    public ClientLocationPageResult pageLocations(long current, long size, String mac, Long userId, LocalDateTime startTime, LocalDateTime endTime) {

        return page(current, size, mac, userId, startTime, endTime);
    }

    @Override
    public ClientLocationPageResult pageOwnedLocations(Long ownerUserId, long current, long size, String mac, LocalDateTime startTime, LocalDateTime endTime) {

        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("缺少有效用户身份");
        }

        return page(current, size, mac, ownerUserId, startTime, endTime);
    }

    private ClientLocationPageResult page(long current, long size, String mac, Long userId, LocalDateTime startTime, LocalDateTime endTime) {

        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {

            throw new IllegalArgumentException("结束时间不能早于开始时间");
        }

        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<ClientLocation> query = new QueryWrapper<>();

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

        Page<ClientLocation> resultPage = clientLocationMapper.selectPage(new Page<>(pageCurrent, pageSize), query);

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

    private LocationSessionContextVO resolveContext(Long userId, Long sessionId) {
        if (!StringUtils.hasText(internalToken)) {
            throw new IllegalStateException("位置 Session 校验功能当前不可用");
        }

        ApiResponse<LocationSessionContextVO> response;

        try {
            response = deviceLocationSessionClient.getLocationContext(sessionId, userId, internalToken);
        } catch (feign.FeignException exception) {
            throw new IllegalStateException(
                    "Session 当前不可用于位置上报，或设备服务暂时不可用"
            );
        }

        LocationSessionContextVO context = response == null ? null : response.getData();

        if (response == null || response.getCode() != 200 || context == null) {
            throw new IllegalStateException("设备服务未返回有效 Session 关系");
        }

        if (!java.util.Objects.equals(userId, context.getUserId())
                || !java.util.Objects.equals(sessionId, context.getSessionId())
                || context.getNodeId() == null
                || !StringUtils.hasText(context.getDeviceCode())
                || !StringUtils.hasText(context.getMac())) {
            throw new IllegalStateException("设备服务返回的 Session 关系不完整");
        }

        return context;
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

        // 刚好到达最小间隔时允许上报。
        if (now.isBefore(nextAllowedTime)) {
            throw new IllegalArgumentException("位置上报过于频繁，请稍后再试");
        }
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

        double elapsedSeconds = elapsedMillis / 1000.0D;

        double distanceMeters = calculateDistanceMeters(previous.getLatitude(), previous.getLongitude(), current.getLatitude(), current.getLongitude());

        // 两次定位精度作为误差缓冲，避免普通GPS漂移被当成异常移动。
        double allowedDistance = maximumSpeedMetersPerSecond * elapsedSeconds + previous.getAccuracy().doubleValue() + current.getAccuracy().doubleValue();

        if (distanceMeters > allowedDistance) {
            throw new IllegalArgumentException("位置变化明显异常，本次上报已拒绝");
        }
    }

    private double calculateDistanceMeters(java.math.BigDecimal firstLatitude, java.math.BigDecimal firstLongitude, java.math.BigDecimal secondLatitude, java.math.BigDecimal secondLongitude) {

        final double earthRadiusMeters = 6371000.0D;

        double firstLatRadians = Math.toRadians(firstLatitude.doubleValue());
        double secondLatRadians = Math.toRadians(secondLatitude.doubleValue());

        double latitudeDelta = Math.toRadians(secondLatitude.doubleValue() - firstLatitude.doubleValue());
        double longitudeDelta = Math.toRadians(secondLongitude.doubleValue() - firstLongitude.doubleValue());

        double sinLatitude = Math.sin(latitudeDelta / 2.0D);
        double sinLongitude = Math.sin(longitudeDelta / 2.0D);

        double haversine = sinLatitude * sinLatitude + Math.cos(firstLatRadians) * Math.cos(secondLatRadians) * sinLongitude * sinLongitude;

        // 避免浮点误差让 haversine 略微超过 1。
        double bounded = Math.min(1.0D, Math.max(0.0D, haversine));

        return 2.0D * earthRadiusMeters * Math.atan2(Math.sqrt(bounded), Math.sqrt(1.0D - bounded));
    }
}