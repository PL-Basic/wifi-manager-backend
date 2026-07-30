package com.plagod.service;

import com.plagod.dto.TrafficAnalyticsQueryCriteria;
import com.plagod.mapper.TrafficLogMapper;
import com.plagod.vo.device.TrafficAnalyticsSourceVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DeviceTrafficAnalyticsQueryService {

    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");

    private static final Set<Integer> ALLOWED_BUCKET_MINUTES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(5, 15, 60, 360, 1440)));

    @Autowired
    private TrafficLogMapper trafficLogMapper;

    @Transactional(readOnly = true)
    public TrafficAnalyticsSourceVO query(Long userId, String mac, Long sessionId, Long nodeId, String deviceCode, LocalDateTime startTime, LocalDateTime endTime, Integer bucketMinutes, Integer topLimit) {

        validate(userId, mac, sessionId, nodeId, deviceCode, startTime, endTime, bucketMinutes, topLimit);

        TrafficAnalyticsQueryCriteria criteria = new TrafficAnalyticsQueryCriteria();

        criteria.setUserId(userId);
        criteria.setMac(normalizeMac(mac));
        criteria.setSessionId(sessionId);
        criteria.setNodeId(nodeId);
        criteria.setDeviceCode(cleanDeviceCode(deviceCode));
        criteria.setStartTime(startTime);
        criteria.setEndTime(endTime);
        criteria.setBucketSeconds(Math.multiplyExact(bucketMinutes, 60));
        criteria.setTopLimit(topLimit);

        TrafficAnalyticsSourceVO result = new TrafficAnalyticsSourceVO();

        result.setUserId(userId);
        result.setMac(criteria.getMac());
        result.setSessionId(sessionId);
        result.setNodeId(nodeId);
        result.setDeviceCode(criteria.getDeviceCode());
        result.setStartTime(startTime);
        result.setEndTime(endTime);
        result.setBucketMinutes(bucketMinutes);
        result.setTopLimit(topLimit);

        result.setSummary(trafficLogMapper.selectAnalyticsSummary(criteria));
        result.setTrend(trafficLogMapper.selectAnalyticsTrend(criteria));
        result.setUsers(rank(criteria, "USER"));
        result.setMacs(rank(criteria, "MAC"));
        result.setSessions(rank(criteria, "SESSION"));
        result.setNodes(rank(criteria, "NODE"));
        result.setDevices(rank(criteria, "DEVICE"));
        result.setDestinationIps(rank(criteria, "DESTINATION_IP"));
        result.setDestinationPorts(rank(criteria, "DESTINATION_PORT"));
        result.setSnis(rank(criteria, "SNI"));
        result.setProtocols(rank(criteria, "PROTOCOL"));

        return result;
    }

    private java.util.List<TrafficAnalyticsSourceVO.RankBucket> rank(TrafficAnalyticsQueryCriteria criteria, String dimension) {

        return trafficLogMapper.selectAnalyticsRanking(criteria, dimension);
    }

    private void validate(Long userId, String mac, Long sessionId, Long nodeId, String deviceCode, LocalDateTime startTime, LocalDateTime endTime, Integer bucketMinutes, Integer topLimit) {

        requirePositive(userId, "userId");
        requirePositive(sessionId, "sessionId");
        requirePositive(nodeId, "nodeId");

        if (StringUtils.hasText(mac) && !MAC_PATTERN.matcher(mac.trim()).matches()) {
            throw new IllegalArgumentException("MAC格式不正确");
        }

        if (StringUtils.hasText(deviceCode) && deviceCode.trim().length() > 64) {
            throw new IllegalArgumentException("deviceCode长度不能超过64");
        }

        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("时间范围无效");
        }

        Duration range = Duration.between(startTime, endTime);

        if (range.compareTo(Duration.ofDays(31)) > 0) {
            throw new IllegalArgumentException("流量统计时间范围不能超过31天");
        }

        if (bucketMinutes == null || !ALLOWED_BUCKET_MINUTES.contains(bucketMinutes)) {
            throw new IllegalArgumentException("bucketMinutes只支持5、15、60、360、1440");
        }

        long rangeMinutes = Math.max(1L, range.toMinutes());
        long bucketCount = (rangeMinutes + bucketMinutes - 1L) / bucketMinutes;

        if (bucketCount > 1000L) {
            throw new IllegalArgumentException("当前时间范围的趋势桶数量超过1000，请增大bucketMinutes");
        }

        if (topLimit == null || topLimit < 1 || topLimit > 50) {
            throw new IllegalArgumentException("topLimit必须在1到50之间");
        }
    }

    private void requirePositive(Long value, String field) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(field + "无效");
        }
    }

    private String normalizeMac(String mac) {
        return StringUtils.hasText(mac) ? mac.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String cleanDeviceCode(String deviceCode) {
        return StringUtils.hasText(deviceCode) ? deviceCode.trim() : null;
    }
}