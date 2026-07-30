package com.plagod.service;

import com.plagod.client.DeviceSignalAnalyticsClient;
import com.plagod.dto.ApiResponse;
import com.plagod.vo.device.SignalAnalyticsSourceVO;
import com.plagod.vo.monitor.SignalAnalysisVO;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SignalAnalyticsService {

    private static final int MIN_VALID_RSSI = -100;
    private static final int MAX_VALID_RSSI = -20;
    private static final int MIN_DISTANCE_SAMPLE_COUNT = 3;
    private static final double MIN_OUTLIER_THRESHOLD_DB = 6.0D;

    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");

    private static final Set<Integer> ALLOWED_BUCKET_MINUTES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(1, 5, 15, 30, 60)));

    @Autowired
    private DeviceSignalAnalyticsClient deviceSignalAnalyticsClient;

    @Value("${wifi.internal.token:}")
    private String internalToken;

    public SignalAnalysisVO query(Long nodeId, String mac, LocalDateTime startTime, LocalDateTime endTime, Integer sampleLimit, Integer bucketMinutes) {

        validateRequest(nodeId, mac, startTime, endTime, sampleLimit, bucketMinutes);

        SignalAnalyticsSourceVO source = loadSource(nodeId, mac, startTime, endTime, sampleLimit, bucketMinutes);

        return analyze(source);
    }

    private SignalAnalyticsSourceVO loadSource(Long nodeId, String mac, LocalDateTime startTime, LocalDateTime endTime, Integer sampleLimit, Integer bucketMinutes) {
        if (!StringUtils.hasText(internalToken)) {
            throw new IllegalStateException("设备信号数据源当前不可用");
        }

        ApiResponse<SignalAnalyticsSourceVO> response;

        try {
            response = deviceSignalAnalyticsClient.querySignals(nodeId, mac, startTime.toString(), endTime.toString(), sampleLimit, bucketMinutes, internalToken);
        } catch (FeignException exception) {
            if (exception.status() == 400) {
                throw new IllegalArgumentException("节点不存在或信号查询参数无效");
            }
            throw new IllegalStateException("设备信号数据源暂时不可用");
        }

        SignalAnalyticsSourceVO source = response == null ? null : response.getData();

        if (response == null || response.getCode() != 200 || source == null) {
            throw new IllegalStateException("设备服务未返回有效信号数据");
        }

        validateSource(nodeId, mac, source);
        return source;
    }

    private SignalAnalysisVO analyze(SignalAnalyticsSourceVO source) {

        List<SignalAnalyticsSourceVO.SignalSample> samples = source.getLatestSamples() == null ? Collections.emptyList() : source.getLatestSamples();

        List<Double> validValues = new ArrayList<>();
        LocalDateTime latestReportTime = null;

        for (SignalAnalyticsSourceVO.SignalSample sample : samples) {
            if (sample == null || sample.getRssi() == null) {
                continue;
            }

            if (sample.getReportTime() != null && (latestReportTime == null || sample.getReportTime().isAfter(latestReportTime))) {
                latestReportTime = sample.getReportTime();
            }

            int rssi = sample.getRssi();

            // 排除明显不可能或无分析意义的 RSSI。
            if (rssi >= MIN_VALID_RSSI && rssi <= MAX_VALID_RSSI) {
                validValues.add((double) rssi);
            }
        }

        List<Double> filteredValues = filterOutliers(validValues);

        SignalAnalysisVO result = createBaseResult(source);
        result.setLatestReportTime(latestReportTime);
        result.setRawSampleCount(samples.size());
        result.setInvalidSampleCount(samples.size() - validValues.size());
        result.setOutlierSampleCount(validValues.size() - filteredValues.size());
        result.setUsedSampleCount(filteredValues.size());
        result.setTrend(buildTrend(source));
        result.setFilterMethod("PHYSICAL_RANGE_AND_MEDIAN_MAD");
        result.setSmoothingMethod("MEDIAN");
        result.setPositioningCapability("ROUGH_DISTANCE_ONLY");
        result.setLimitation("单节点RSSI只能估算粗略距离，不能确定方向或二维坐标；墙体、人体遮挡、多径和终端发射功率都会造成偏差");

        if (filteredValues.isEmpty()) {
            result.setQualityLevel("NO_DATA");
            result.setQualityDescription("没有可用于分析的有效RSSI样本");
            result.setConfidenceLevel("INSUFFICIENT");
            result.setConfidenceDescription("有效样本不足，未生成距离估算");
            return result;
        }

        double smoothedRssi = median(filteredValues);
        int uncertaintyDb = calculateUncertaintyDb(filteredValues, smoothedRssi);

        result.setSmoothedRssi(decimal(smoothedRssi, 2));
        result.setRssiUncertaintyDb(uncertaintyDb);
        result.setQualityLevel(qualityLevel(smoothedRssi));
        result.setQualityDescription(qualityDescription(smoothedRssi));

        if (filteredValues.size() < MIN_DISTANCE_SAMPLE_COUNT) {
            result.setConfidenceLevel("INSUFFICIENT");result.setConfidenceDescription("至少需要3个有效样本才能生成粗略距离");
            return result;
        }

        double pathLossExponent = source.getPathLossExponent().doubleValue();

        result.setEstimatedDistanceMeters(calculateDistance(smoothedRssi, source.getRssiAtOneMeter(), pathLossExponent));

        double strongestRssi = clamp(smoothedRssi + uncertaintyDb, MIN_VALID_RSSI, MAX_VALID_RSSI);

        double weakestRssi = clamp(smoothedRssi - uncertaintyDb, MIN_VALID_RSSI, MAX_VALID_RSSI);

        result.setMinimumDistanceMeters(calculateDistance(strongestRssi, source.getRssiAtOneMeter(), pathLossExponent));
        result.setMaximumDistanceMeters(calculateDistance(weakestRssi, source.getRssiAtOneMeter(), pathLossExponent));

        double outlierRatio = validValues.isEmpty() ? 1.0D : (double) result.getOutlierSampleCount() / validValues.size();

        if (filteredValues.size() >= 9 && uncertaintyDb <= 6 && outlierRatio <= 0.20D) {
            result.setConfidenceLevel("MEDIUM");
            result.setConfidenceDescription("样本数量和稳定性较好，但单节点RSSI仍只适合粗略距离判断");
        } else {
            result.setConfidenceLevel("LOW");
            result.setConfidenceDescription("样本较少或波动较大，距离结果仅供趋势参考");
        }

        return result;
    }

    private List<Double> filterOutliers(List<Double> values) {

        if (values.size() < MIN_DISTANCE_SAMPLE_COUNT) {
            return new ArrayList<>(values);
        }

        double center = median(values);
        List<Double> deviations = new ArrayList<>();

        for (Double value : values) {
            deviations.add(Math.abs(value - center));
        }

        double medianAbsoluteDeviation = median(deviations);
        double threshold = Math.max(MIN_OUTLIER_THRESHOLD_DB, medianAbsoluteDeviation * 3.0D);

        List<Double> filtered = new ArrayList<>();

        for (Double value : values) {
            if (Math.abs(value - center) <= threshold) {
                filtered.add(value);
            }
        }

        return filtered;
    }

    private int calculateUncertaintyDb(List<Double> values, double center) {

        List<Double> deviations = new ArrayList<>();

        for (Double value : values) {
            deviations.add(Math.abs(value - center));
        }

        double robustDeviation = median(deviations) * 1.4826D;

        // 最少保留4dB误差，最高限制为12dB。
        return (int) Math.ceil(Math.max(4.0D, Math.min(12.0D, robustDeviation)));
    }

    private List<SignalAnalysisVO.SignalTrendPoint> buildTrend(SignalAnalyticsSourceVO source) {

        List<SignalAnalysisVO.SignalTrendPoint> result = new ArrayList<>();

        if (source.getTrend() == null) {
            return result;
        }

        for (SignalAnalyticsSourceVO.SignalTrendBucket bucket : source.getTrend()) {

            if (bucket == null) {
                continue;
            }

            SignalAnalysisVO.SignalTrendPoint point = new SignalAnalysisVO.SignalTrendPoint();

            point.setBucketTime(bucket.getBucketTime());
            point.setSampleCount(bucket.getSampleCount());
            point.setAverageRssi(bucket.getAverageRssi());
            point.setMinRssi(bucket.getMinRssi());
            point.setMaxRssi(bucket.getMaxRssi());

            if (bucket.getAverageRssi() != null) {
                double averageRssi = bucket.getAverageRssi().doubleValue();

                point.setQualityLevel(qualityLevel(averageRssi));
                point.setEstimatedDistanceMeters(calculateDistance(averageRssi, source.getRssiAtOneMeter(), source.getPathLossExponent().doubleValue()));
            }

            result.add(point);
        }

        return result;
    }

    private SignalAnalysisVO createBaseResult(SignalAnalyticsSourceVO source) {

        SignalAnalysisVO result = new SignalAnalysisVO();

        result.setNodeId(source.getNodeId());
        result.setDeviceCode(source.getDeviceCode());
        result.setMac(source.getMac());
        result.setStartTime(source.getStartTime());
        result.setEndTime(source.getEndTime());
        result.setRssiAtOneMeter(source.getRssiAtOneMeter());
        result.setPathLossExponent(source.getPathLossExponent());

        return result;
    }

    private BigDecimal calculateDistance(double rssi, int rssiAtOneMeter, double pathLossExponent) {

        double exponent = (rssiAtOneMeter - rssi) / (10.0D * pathLossExponent);

        double meters = Math.pow(10.0D, exponent);

        return decimal(meters, 2);
    }

    private double median(List<Double> values) {

        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        int middle = sorted.size() / 2;

        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }

        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0D;
    }

    private String qualityLevel(double rssi) {
        if (rssi >= -50.0D) {
            return "EXCELLENT";
        }
        if (rssi >= -60.0D) {
            return "GOOD";
        }
        if (rssi >= -70.0D) {
            return "FAIR";
        }
        if (rssi >= -80.0D) {
            return "WEAK";
        }
        return "POOR";
    }

    private String qualityDescription(double rssi) {
        if (rssi >= -50.0D) {
            return "信号优秀";
        }
        if (rssi >= -60.0D) {
            return "信号良好";
        }
        if (rssi >= -70.0D) {
            return "信号一般";
        }
        if (rssi >= -80.0D) {
            return "信号较弱";
        }
        return "信号很弱";
    }

    private void validateSource(Long nodeId, String mac, SignalAnalyticsSourceVO source) {

        if (!Objects.equals(nodeId, source.getNodeId()) || !StringUtils.hasText(source.getMac()) || !mac.trim().equalsIgnoreCase(source.getMac())) {
            throw new IllegalStateException("设备服务返回的信号对象不匹配");
        }

        if (source.getRssiAtOneMeter() == null
                || source.getRssiAtOneMeter() < MIN_VALID_RSSI
                || source.getRssiAtOneMeter() > MAX_VALID_RSSI
                || source.getPathLossExponent() == null
                || source.getPathLossExponent().compareTo(BigDecimal.ONE) < 0
                || source.getPathLossExponent().compareTo(new BigDecimal("6.00")) > 0) {
            throw new IllegalStateException("节点信号标定参数无效");
        }
    }

    private void validateRequest(Long nodeId, String mac, LocalDateTime startTime, LocalDateTime endTime, Integer sampleLimit, Integer bucketMinutes) {

        if (nodeId == null || nodeId <= 0) {
            throw new IllegalArgumentException("nodeId无效");
        }

        if (!StringUtils.hasText(mac) || !MAC_PATTERN.matcher(mac.trim()).matches()) {
            throw new IllegalArgumentException("MAC格式不正确");
        }

        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("时间范围无效");
        }

        if (Duration.between(startTime, endTime).compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("信号分析时间范围不能超过24小时");
        }

        if (sampleLimit == null || sampleLimit < 3 || sampleLimit > 101) {
            throw new IllegalArgumentException("sampleLimit必须在3到101之间");
        }

        if (bucketMinutes == null || !ALLOWED_BUCKET_MINUTES.contains(bucketMinutes)) {
            throw new IllegalArgumentException("bucketMinutes只支持1、5、15、30、60");
        }
    }

    private BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}