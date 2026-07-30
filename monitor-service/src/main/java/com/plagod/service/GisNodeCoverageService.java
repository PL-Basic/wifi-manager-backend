package com.plagod.service;

import com.plagod.client.DeviceSignalAnalyticsClient;
import com.plagod.dto.ApiResponse;
import com.plagod.util.GeoMath;
import com.plagod.vo.device.SignalAnalyticsSourceVO;
import com.plagod.vo.monitor.GisNodeCoverageVO;
import com.plagod.vo.monitor.GisTrajectoryVO;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class GisNodeCoverageService {

    private static final int MAXIMUM_SIGNAL_SAMPLES = 5000;
    private static final int MIN_VALID_RSSI = -100;
    private static final int MAX_VALID_RSSI = -20;

    @Autowired
    private GisAnalyticsService gisAnalyticsService;

    @Autowired
    private DeviceSignalAnalyticsClient deviceSignalAnalyticsClient;

    @Value("${wifi.internal.token:}")
    private String internalToken;

    public GisNodeCoverageVO query(Long sessionId, LocalDateTime startTime, LocalDateTime endTime, Double maximumAccuracyMeters, Integer matchToleranceSeconds) {

        validateRequest(sessionId, startTime, endTime, maximumAccuracyMeters, matchToleranceSeconds);

        // 直接复用已经完成的轨迹清洗，避免覆盖分析重新定义 GPS 可信规则。
        GisTrajectoryVO trajectory = gisAnalyticsService.queryTrajectory(sessionId, startTime, endTime, maximumAccuracyMeters);

        if (trajectory.getPoints() == null || trajectory.getPoints().isEmpty()) {
            throw new IllegalArgumentException("当前时间范围没有可用于覆盖分析的可信GPS点");
        }

        if (trajectory.getNodeId() == null || trajectory.getNodeId() <= 0 || !StringUtils.hasText(trajectory.getMac())) {
            throw new IllegalStateException("可信GPS轨迹缺少节点或MAC绑定");
        }

        SignalAnalyticsSourceVO source = loadSignalSource(trajectory, sessionId, startTime, endTime);

        validateSignalSource(trajectory, sessionId, source);

        SamplePool samplePool = buildSamplePool(sessionId, source.getLatestSamples());

        return buildResult(trajectory, source, samplePool, maximumAccuracyMeters, matchToleranceSeconds);
    }

    private SignalAnalyticsSourceVO loadSignalSource(GisTrajectoryVO trajectory, Long sessionId, LocalDateTime startTime, LocalDateTime endTime) {

        if (!StringUtils.hasText(internalToken)) {
            throw new IllegalStateException("设备信号数据源当前不可用");
        }

        ApiResponse<SignalAnalyticsSourceVO> response;

        try {
            response = deviceSignalAnalyticsClient.queryCoverageSignals(trajectory.getNodeId(), trajectory.getMac(), sessionId, startTime.toString(), endTime.toString(), MAXIMUM_SIGNAL_SAMPLES, internalToken);
        } catch (FeignException exception) {
            if (exception.status() == 400) {
                throw new IllegalArgumentException("节点不存在、未配置安装坐标或RSSI查询范围无效");
            }
            throw new IllegalStateException("设备信号数据源暂时不可用");
        }

        if (response == null || response.getCode() != 200 || response.getData() == null) {
            throw new IllegalStateException("设备服务未返回有效覆盖分析数据");
        }

        return response.getData();
    }

    private void validateSignalSource(GisTrajectoryVO trajectory, Long sessionId, SignalAnalyticsSourceVO source) {

        if (!Objects.equals(trajectory.getNodeId(), source.getNodeId()) || !Objects.equals(sessionId, source.getSessionId()) || source.getMac() == null || !source.getMac().equalsIgnoreCase(trajectory.getMac())) {
            throw new IllegalStateException("RSSI数据源与GPS轨迹绑定不一致");
        }

        if (source.getNodeLatitude() == null || source.getNodeLongitude() == null) {
            throw new IllegalArgumentException("节点尚未配置安装坐标");
        }

        double latitude = source.getNodeLatitude().doubleValue();
        double longitude = source.getNodeLongitude().doubleValue();

        if (!Double.isFinite(latitude)
                || latitude < -90.0D
                || latitude > 90.0D
                || !Double.isFinite(longitude)
                || longitude < -180.0D
                || longitude > 180.0D) {
            throw new IllegalStateException("节点安装坐标无效");
        }

        if (source.getRssiAtOneMeter() == null
                || source.getRssiAtOneMeter() < MIN_VALID_RSSI
                || source.getRssiAtOneMeter() > MAX_VALID_RSSI
                || source.getPathLossExponent() == null
                || source.getPathLossExponent().doubleValue() <= 0.0D) {
            throw new IllegalStateException("节点RSSI标定参数无效");
        }
    }

    private SamplePool buildSamplePool(Long sessionId, List<SignalAnalyticsSourceVO.SignalSample> samples) {

        NavigableMap<LocalDateTime, Deque<SignalAnalyticsSourceVO.SignalSample>> available = new TreeMap<>();

        int rawCount = samples == null ? 0 : samples.size();
        int ignoredCount = 0;
        int usableCount = 0;

        if (samples != null) {
            for (SignalAnalyticsSourceVO.SignalSample sample : samples) {
                if (sample == null || sample.getId() == null || sample.getReportTime() == null || sample.getRssi() == null) {
                    ignoredCount++;
                    continue;
                }

                // device-service 查询必须已经完成精确 Session 过滤。
                if (!Objects.equals(sessionId, sample.getSessionId())) {
                    throw new IllegalStateException("RSSI数据源混入了其他Session样本");
                }

                if (sample.getRssi() < MIN_VALID_RSSI || sample.getRssi() > MAX_VALID_RSSI) {
                    ignoredCount++;
                    continue;
                }

                available
                        .computeIfAbsent(sample.getReportTime(), ignored -> new ArrayDeque<>())
                        .addLast(sample);

                usableCount++;
            }
        }

        return new SamplePool(available, rawCount, ignoredCount, usableCount);
    }

    private GisNodeCoverageVO buildResult(GisTrajectoryVO trajectory, SignalAnalyticsSourceVO source, SamplePool samplePool, Double maximumAccuracyMeters, Integer matchToleranceSeconds) {

        GisNodeCoverageVO result = new GisNodeCoverageVO();

        result.setCoordinateSystem("WGS84");
        result.setAnalysisPurpose("NODE_COVERAGE_AND_RSSI_CALIBRATION");
        result.setPositioningCapability("COVERAGE_CALIBRATION_ONLY");
        result.setLimitation("RSSI仅提供受遮挡、多径和终端发射功率影响的粗略距离，结果不能用于确定方向或二维位置");

        result.setSessionId(trajectory.getSessionId());
        result.setUserId(trajectory.getUserId());
        result.setNodeId(source.getNodeId());
        result.setDeviceCode(source.getDeviceCode());
        result.setMac(source.getMac());

        result.setNodeLatitude(source.getNodeLatitude());
        result.setNodeLongitude(source.getNodeLongitude());
        result.setRssiAtOneMeter(source.getRssiAtOneMeter());
        result.setPathLossExponent(source.getPathLossExponent());

        result.setStartTime(trajectory.getStartTime());
        result.setEndTime(trajectory.getEndTime());
        result.setMaximumAccuracyMeters(maximumAccuracyMeters);
        result.setMatchToleranceSeconds(matchToleranceSeconds);

        List<GisNodeCoverageVO.CoverageObservation> observations = new ArrayList<>();

        int unmatchedGpsCount = 0;
        double maximumObservedDistance = 0.0D;
        double absoluteErrorSum = 0.0D;
        double errorRatioSum = 0.0D;
        int errorRatioCount = 0;

        long toleranceMillis = matchToleranceSeconds * 1000L;

        for (GisTrajectoryVO.TrajectoryPoint point : trajectory.getPoints()) {

            SignalMatch match = pollNearestSample(samplePool.available, point.getReportTime(), toleranceMillis);

            if (match == null) {
                unmatchedGpsCount++;
                continue;
            }

            double actualDistance = GeoMath.distanceMeters(source.getNodeLatitude(), source.getNodeLongitude(), point.getLatitude(), point.getLongitude());

            double estimatedDistance = calculateEstimatedDistance(match.sample.getRssi(), source.getRssiAtOneMeter(), source.getPathLossExponent().doubleValue());
            double absoluteError = Math.abs(estimatedDistance - actualDistance);

            BigDecimal errorRatio = null;

            // 距离过小时比例误差没有稳定意义，因此只返回绝对误差。
            if (actualDistance >= 1.0D) {
                double ratio = absoluteError / actualDistance;
                errorRatio = GeoMath.decimal(ratio, 4);
                errorRatioSum += ratio;
                errorRatioCount++;
            }

            maximumObservedDistance = Math.max(maximumObservedDistance, actualDistance);
            absoluteErrorSum += absoluteError;

            observations.add(createObservation(point, match, actualDistance, estimatedDistance, absoluteError, errorRatio, toleranceMillis));
        }

        int matchedCount = observations.size();

        result.setGpsPointCount(trajectory.getPoints().size());
        result.setRssiSampleCount(samplePool.rawCount);
        result.setIgnoredRssiSampleCount(samplePool.ignoredCount);
        result.setMatchedPointCount(matchedCount);
        result.setUnmatchedGpsPointCount(unmatchedGpsCount);
        result.setUnusedRssiSampleCount(Math.max(0, samplePool.usableCount - matchedCount));

        if (matchedCount > 0) {
            result.setMaximumObservedDistanceMeters(GeoMath.decimal(maximumObservedDistance, 2));
            result.setAverageAbsoluteErrorMeters(GeoMath.decimal(absoluteErrorSum / matchedCount, 2));
        }

        if (errorRatioCount > 0) {result.setAverageErrorRatio(GeoMath.decimal(errorRatioSum / errorRatioCount, 4));
        }

        result.setObservations(observations);
        return result;
    }

    private GisNodeCoverageVO.CoverageObservation createObservation(GisTrajectoryVO.TrajectoryPoint point, SignalMatch match, double actualDistance, double estimatedDistance, double absoluteError, BigDecimal errorRatio, long toleranceMillis) {

        GisNodeCoverageVO.CoverageObservation observation = new GisNodeCoverageVO.CoverageObservation();

        observation.setLocationId(point.getLocationId());
        observation.setSignalId(match.sample.getId());
        observation.setLatitude(point.getLatitude());
        observation.setLongitude(point.getLongitude());
        observation.setAccuracy(point.getAccuracy());
        observation.setLocationReportTime(point.getReportTime());
        observation.setSignalReportTime(match.sample.getReportTime());
        observation.setTimeDifferenceMillis(match.timeDifferenceMillis);
        observation.setRssi(match.sample.getRssi());

        observation.setActualDistanceMeters(GeoMath.decimal(actualDistance, 2));
        observation.setEstimatedDistanceMeters(GeoMath.decimal(estimatedDistance, 2));
        observation.setAbsoluteErrorMeters(GeoMath.decimal(absoluteError, 2));
        observation.setErrorRatio(errorRatio);

        setConfidence(observation, point.getAccuracy(), match.timeDifferenceMillis, toleranceMillis);

        GisNodeCoverageVO.GeoJsonPoint geometry = new GisNodeCoverageVO.GeoJsonPoint();

        geometry.setType("Point");
        geometry.setCoordinates(Arrays.asList(point.getLongitude(), point.getLatitude()));

        observation.setGeometry(geometry);
        return observation;
    }

    private SignalMatch pollNearestSample(NavigableMap<LocalDateTime, Deque<SignalAnalyticsSourceVO.SignalSample>> available, LocalDateTime targetTime, long toleranceMillis) {

        if (targetTime == null || available.isEmpty()) {
            return null;
        }

        Map.Entry<LocalDateTime, Deque<SignalAnalyticsSourceVO.SignalSample>> before = available.floorEntry(targetTime);

        Map.Entry<LocalDateTime, Deque<SignalAnalyticsSourceVO.SignalSample>> after = available.ceilingEntry(targetTime);

        Map.Entry<LocalDateTime, Deque<SignalAnalyticsSourceVO.SignalSample>> selected = selectNearest(targetTime, before, after);

        if (selected == null) {
            return null;
        }

        long difference = absoluteTimeDifferenceMillis(targetTime, selected.getKey());
        if (difference > toleranceMillis) {
            return null;
        }

        SignalAnalyticsSourceVO.SignalSample sample = selected.getValue().removeFirst();

        if (selected.getValue().isEmpty()) {
            available.remove(selected.getKey());
        }

        return new SignalMatch(sample, difference);
    }

    private Map.Entry<LocalDateTime, Deque<SignalAnalyticsSourceVO.SignalSample>> selectNearest(LocalDateTime targetTime, Map.Entry<LocalDateTime, Deque<SignalAnalyticsSourceVO.SignalSample>> before, Map.Entry<LocalDateTime, Deque<SignalAnalyticsSourceVO.SignalSample>> after) {
        if (before == null) {
            return after;
        }
        if (after == null) {
            return before;
        }
        if (before.getKey().equals(after.getKey())) {
            return before;
        }

        long beforeDifference = absoluteTimeDifferenceMillis(targetTime, before.getKey());
        long afterDifference = absoluteTimeDifferenceMillis(targetTime, after.getKey());

        // 时间差相同时优先使用更早的样本，结果保持稳定。
        return beforeDifference <= afterDifference ? before : after;
    }

    private long absoluteTimeDifferenceMillis(LocalDateTime first, LocalDateTime second) {

        return Math.abs(Duration.between(first, second).toMillis());
    }

    private double calculateEstimatedDistance(int rssi, int rssiAtOneMeter, double pathLossExponent) {

        double exponent = (rssiAtOneMeter - rssi) / (10.0D * pathLossExponent);

        return Math.pow(10.0D, exponent);
    }

    private void setConfidence(GisNodeCoverageVO.CoverageObservation observation, BigDecimal accuracy, long timeDifferenceMillis, long toleranceMillis) {

        double accuracyMeters = accuracy == null ? Double.POSITIVE_INFINITY : accuracy.doubleValue();

        if (accuracyMeters <= 10.0D && timeDifferenceMillis <= toleranceMillis / 3L) {
            observation.setConfidenceLevel("HIGH");
            observation.setConfidenceDescription("GPS精度和采样时间差较好，但RSSI距离仍可能受遮挡与多径影响");
            return;
        }

        if (accuracyMeters <= 30.0D && timeDifferenceMillis <= toleranceMillis * 2L / 3L) {
            observation.setConfidenceLevel("MEDIUM");
            observation.setConfidenceDescription("GPS精度或采样时间存在一定偏差，结果仅适合粗略覆盖观察");
            return;
        }

        observation.setConfidenceLevel("LOW");
        observation.setConfidenceDescription("GPS精度或采样时间差较大，不能据此判断精确覆盖边界");
    }

    private void validateRequest(Long sessionId, LocalDateTime startTime, LocalDateTime endTime, Double maximumAccuracyMeters, Integer matchToleranceSeconds) {

        if (sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException("sessionId无效");
        }

        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("时间范围无效");
        }

        if (Duration.between(startTime, endTime).compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("节点覆盖分析时间范围不能超过24小时");
        }

        if (maximumAccuracyMeters == null || !Double.isFinite(maximumAccuracyMeters) || maximumAccuracyMeters < 1.0D || maximumAccuracyMeters > 1000.0D) {
            throw new IllegalArgumentException("最大定位误差必须在1到1000米之间");
        }

        if (matchToleranceSeconds == null || matchToleranceSeconds < 1 || matchToleranceSeconds > 300) {
            throw new IllegalArgumentException("时间匹配容差必须在1到300秒之间");
        }
    }

    private static class SamplePool {
        private final NavigableMap<LocalDateTime, Deque<SignalAnalyticsSourceVO.SignalSample>> available;
        private final int rawCount;
        private final int ignoredCount;
        private final int usableCount;

        private SamplePool(NavigableMap<LocalDateTime, Deque<SignalAnalyticsSourceVO.SignalSample>> available, int rawCount, int ignoredCount, int usableCount) {

            this.available = available;
            this.rawCount = rawCount;
            this.ignoredCount = ignoredCount;
            this.usableCount = usableCount;
        }
    }

    private static class SignalMatch {
        private final SignalAnalyticsSourceVO.SignalSample sample;
        private final long timeDifferenceMillis;

        private SignalMatch(SignalAnalyticsSourceVO.SignalSample sample, long timeDifferenceMillis) {

            this.sample = sample;
            this.timeDifferenceMillis = timeDifferenceMillis;
        }
    }
}