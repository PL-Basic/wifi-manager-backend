package com.plagod.service;

import com.plagod.entity.device.Esp32Node;
import com.plagod.mapper.ClientSignalMapper;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.vo.device.SignalAnalyticsSourceVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class DeviceSignalAnalyticsQueryService {

    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");

    private static final Set<Integer> ALLOWED_BUCKET_MINUTES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(1, 5, 15, 30, 60)));

    private static final int MAXIMUM_COVERAGE_SAMPLES = 5000;

    @Autowired
    private Esp32NodeMapper nodeMapper;

    @Autowired
    private ClientSignalMapper signalMapper;

    public SignalAnalyticsSourceVO query(Long nodeId, String mac, LocalDateTime startTime, LocalDateTime endTime, Integer sampleLimit, Integer bucketMinutes) {

        validate(nodeId, startTime, endTime, sampleLimit, bucketMinutes);

        String normalizedMac = normalizeMac(mac);
        Esp32Node node = requireNode(nodeId);

        List<SignalAnalyticsSourceVO.SignalSample> samples = signalMapper.selectLatestSamples(nodeId, normalizedMac, startTime, endTime, sampleLimit);

        // SQL 先取最新 N 条，再恢复为时间正序。
        Collections.reverse(samples);

        SignalAnalyticsSourceVO result = createBaseResult(node, normalizedMac, null, startTime, endTime, sampleLimit);

        result.setLatestSamples(samples);
        result.setTrend(signalMapper.selectTrendBuckets(nodeId, normalizedMac, startTime, endTime, Math.multiplyExact(bucketMinutes, 60)));

        return result;
    }

    public SignalAnalyticsSourceVO queryCoverage(Long nodeId, String mac, Long sessionId, LocalDateTime startTime, LocalDateTime endTime, Integer sampleLimit) {

        validateCoverage(nodeId, sessionId, startTime, endTime, sampleLimit);

        String normalizedMac = normalizeMac(mac);
        Esp32Node node = requireNode(nodeId);

        if (node.getLatitude() == null || node.getLongitude() == null) {
            throw new IllegalArgumentException("节点尚未配置安装坐标");
        }

        // 多取一条，用于判断数据是否超过上限，避免静默截断。
        List<SignalAnalyticsSourceVO.SignalSample> samples = signalMapper.selectCoverageSamples(nodeId, normalizedMac, sessionId, startTime, endTime, sampleLimit + 1);
        if (samples.size() > sampleLimit) {
            throw new IllegalArgumentException("覆盖分析的RSSI样本超过上限，请缩小时间范围");
        }

        SignalAnalyticsSourceVO result = createBaseResult(node, normalizedMac, sessionId, startTime, endTime, sampleLimit);

        result.setLatestSamples(samples);
        result.setTrend(Collections.emptyList());
        return result;
    }

    private SignalAnalyticsSourceVO createBaseResult(Esp32Node node, String mac, Long sessionId, LocalDateTime startTime, LocalDateTime endTime, Integer sampleLimit) {

        SignalAnalyticsSourceVO result = new SignalAnalyticsSourceVO();

        result.setNodeId(node.getNodeId());
        result.setDeviceCode(node.getDeviceCode());
        result.setMac(mac);
        result.setSessionId(sessionId);
        result.setNodeLatitude(node.getLatitude());
        result.setNodeLongitude(node.getLongitude());
        result.setRssiAtOneMeter(node.getRssiAtOneMeter() == null ? -59 : node.getRssiAtOneMeter());
        result.setPathLossExponent(node.getPathLossExponent() == null ? new BigDecimal("2.00") : node.getPathLossExponent());
        result.setStartTime(startTime);
        result.setEndTime(endTime);
        result.setSampleLimit(sampleLimit);

        return result;
    }

    private Esp32Node requireNode(Long nodeId) {
        Esp32Node node = nodeMapper.selectById(nodeId);

        if (node == null) {
            throw new IllegalArgumentException("节点不存在");
        }
        return node;
    }

    private void validate(Long nodeId, LocalDateTime startTime, LocalDateTime endTime, Integer sampleLimit, Integer bucketMinutes) {

        validateCommon(nodeId, startTime, endTime);

        if (sampleLimit == null || sampleLimit < 3 || sampleLimit > 101) {
            throw new IllegalArgumentException("sampleLimit必须在3到101之间");
        }
        if (bucketMinutes == null || !ALLOWED_BUCKET_MINUTES.contains(bucketMinutes)) {
            throw new IllegalArgumentException("bucketMinutes只支持1、5、15、30、60");
        }
    }

    private void validateCoverage(Long nodeId, Long sessionId, LocalDateTime startTime, LocalDateTime endTime, Integer sampleLimit) {

        validateCommon(nodeId, startTime, endTime);

        if (sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException("sessionId无效");
        }
        if (sampleLimit == null || sampleLimit < 1 || sampleLimit > MAXIMUM_COVERAGE_SAMPLES) {
            throw new IllegalArgumentException("覆盖分析sampleLimit必须在1到5000之间");
        }
    }

    private void validateCommon(Long nodeId, LocalDateTime startTime, LocalDateTime endTime) {

        if (nodeId == null || nodeId <= 0) {
            throw new IllegalArgumentException("nodeId无效");
        }
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("时间范围无效");
        }
        if (Duration.between(startTime, endTime).compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("信号分析时间范围不能超过24小时");
        }
    }

    private String normalizeMac(String mac) {
        if (!StringUtils.hasText(mac)) {
            throw new IllegalArgumentException("MAC不能为空");
        }

        String value = mac.trim().toUpperCase(Locale.ROOT);

        if (!MAC_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("MAC格式不正确");
        }
        return value;
    }
}