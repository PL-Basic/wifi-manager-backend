package com.plagod.service;

import com.plagod.entity.monitor.ClientLocation;
import com.plagod.mapper.ClientLocationMapper;
import com.plagod.util.GeoMath;
import com.plagod.vo.monitor.GisHeatmapVO;
import com.plagod.vo.monitor.GisPointFilterStatsVO;
import com.plagod.vo.monitor.GisStayPointResultVO;
import com.plagod.vo.monitor.GisTrajectoryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class GisAnalyticsService {

    private static final String COORDINATE_SYSTEM = "WGS84";
    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");

    @Autowired
    private ClientLocationMapper locationMapper;

    @Value("${wifi.gis.maximum-query-points:5000}")
    private int maximumQueryPoints;

    @Value("${wifi.gis.maximum-query-days:7}")
    private long maximumQueryDays;

    @Value("${wifi.location.maximum-speed-meters-per-second:100}")
    private double maximumSpeedMetersPerSecond;

    public GisTrajectoryVO queryTrajectory(Long sessionId, LocalDateTime startTime, LocalDateTime endTime, Double maximumAccuracyMeters) {
        requireSessionId(sessionId);
        validateQuery(startTime, endTime, maximumAccuracyMeters);

        CleanResult cleanResult = loadAndClean(sessionId, null, null, null, startTime, endTime, maximumAccuracyMeters);

        GisTrajectoryVO result = new GisTrajectoryVO();
        result.setCoordinateSystem(COORDINATE_SYSTEM);
        result.setSessionId(sessionId);
        result.setStartTime(startTime);
        result.setEndTime(endTime);
        result.setFilterStats(cleanResult.stats);

        ClientLocation identity = cleanResult.identity;
        if (identity != null) {
            result.setUserId(identity.getUserId());
            result.setNodeId(identity.getNodeId());
            result.setDeviceCode(identity.getDeviceCode());
            result.setMac(identity.getMac());
        }

        List<GisTrajectoryVO.TrajectoryPoint> points = new ArrayList<>();
        List<List<BigDecimal>> coordinates = new ArrayList<>();
        double totalDistance = 0.0D;

        for (CleanPoint cleanPoint : cleanResult.points) {
            ClientLocation source = cleanPoint.source;

            GisTrajectoryVO.TrajectoryPoint point = new GisTrajectoryVO.TrajectoryPoint();

            point.setLocationId(source.getId());
            point.setLatitude(source.getLatitude());
            point.setLongitude(source.getLongitude());
            point.setAccuracy(source.getAccuracy());
            point.setReportTime(source.getReportTime());
            point.setElapsedSeconds(cleanPoint.elapsedSeconds);
            point.setDistanceFromPreviousMeters(GeoMath.decimal(cleanPoint.distanceFromPreviousMeters, 2));

            points.add(point);
            coordinates.add(Arrays.asList(source.getLongitude(), source.getLatitude()));

            totalDistance += cleanPoint.distanceFromPreviousMeters;
        }

        result.setPoints(points);
        result.setTotalDistanceMeters(GeoMath.decimal(totalDistance, 2));
        result.setDurationSeconds(calculateDuration(cleanResult.points));

        if (coordinates.size() >= 2) {
            GisTrajectoryVO.GeoJsonLineString geometry = new GisTrajectoryVO.GeoJsonLineString();

            geometry.setType("LineString");
            geometry.setCoordinates(coordinates);
            result.setGeometry(geometry);
        } else {
            // 单点仍通过 points 返回，但不能伪装成合法 LineString。
            result.setGeometry(null);
        }


        return result;
    }

    public GisStayPointResultVO queryStayPoints(Long sessionId, LocalDateTime startTime, LocalDateTime endTime, Double maximumAccuracyMeters, Integer radiusMeters, Long minimumStaySeconds) {
        requireSessionId(sessionId);
        validateQuery(startTime, endTime, maximumAccuracyMeters);

        if (radiusMeters == null || radiusMeters < 5 || radiusMeters > 1000) {
            throw new IllegalArgumentException("停留半径必须在5到1000米之间");
        }

        if (minimumStaySeconds == null || minimumStaySeconds < 60 || minimumStaySeconds > 86400) {
            throw new IllegalArgumentException("最短停留时间必须在60到86400秒之间");
        }

        CleanResult cleanResult = loadAndClean(sessionId, null, null, null, startTime, endTime, maximumAccuracyMeters);

        GisStayPointResultVO result = new GisStayPointResultVO();
        result.setCoordinateSystem(COORDINATE_SYSTEM);
        result.setSessionId(sessionId);
        result.setStartTime(startTime);
        result.setEndTime(endTime);
        result.setRadiusMeters(radiusMeters);
        result.setMinimumStaySeconds(minimumStaySeconds);
        result.setFilterStats(cleanResult.stats);

        List<GisStayPointResultVO.StayPoint> stayPoints = detectStayPoints(cleanResult.points, radiusMeters, minimumStaySeconds);

        result.setStayPoints(stayPoints);
        return result;
    }

    public GisHeatmapVO queryHeatmap(Long userId, Long sessionId, Long nodeId, String mac, LocalDateTime startTime, LocalDateTime endTime, Double maximumAccuracyMeters, Integer gridSizeMeters) {

        validateQuery(startTime, endTime, maximumAccuracyMeters);

        if (userId != null && userId <= 0) {
            throw new IllegalArgumentException("userId无效");
        }
        if (sessionId != null && sessionId <= 0) {
            throw new IllegalArgumentException("sessionId无效");
        }
        if (nodeId != null && nodeId <= 0) {
            throw new IllegalArgumentException("nodeId无效");
        }
        if (gridSizeMeters == null || gridSizeMeters < 10 || gridSizeMeters > 1000) {
            throw new IllegalArgumentException("热力网格大小必须在10到1000米之间");
        }

        String normalizedMac = normalizeOptionalMac(mac);

        CleanResult cleanResult = loadAndClean(sessionId, userId, nodeId, normalizedMac, startTime, endTime, maximumAccuracyMeters);

        GisHeatmapVO result = new GisHeatmapVO();
        result.setCoordinateSystem(COORDINATE_SYSTEM);
        result.setUserId(userId);
        result.setSessionId(sessionId);
        result.setNodeId(nodeId);
        result.setMac(normalizedMac);
        result.setStartTime(startTime);
        result.setEndTime(endTime);
        result.setGridSizeMeters(gridSizeMeters);
        result.setFilterStats(cleanResult.stats);

        List<GisHeatmapVO.HeatGrid> grids = aggregateHeatmap(cleanResult.points, gridSizeMeters);

        int maximumCellCount = grids.stream()
                .map(GisHeatmapVO.HeatGrid::getPointCount)
                .max(Integer::compareTo)
                .orElse(0);

        result.setGrids(grids);
        result.setMaximumCellPointCount(maximumCellCount);
        result.setTotalAggregatedPointCount(
                grids.stream()
                        .mapToInt(GisHeatmapVO.HeatGrid::getPointCount)
                        .sum());

        return result;
    }

    private CleanResult loadAndClean(Long sessionId, Long userId, Long nodeId, String mac, LocalDateTime startTime, LocalDateTime endTime, Double maximumAccuracyMeters) {

        validateConfiguration();

        List<ClientLocation> source = locationMapper.selectTrustedPointsForGis(sessionId, userId, nodeId, mac, startTime, endTime, maximumQueryPoints + 1);

        if (source.size() > maximumQueryPoints) {
            throw new IllegalArgumentException("位置点数量超过查询上限，请缩小时间范围");
        }

        return clean(source, maximumAccuracyMeters);
    }

    private CleanResult clean(List<ClientLocation> source,
                              double maximumAccuracyMeters) {

        List<ClientLocation> ordered = new ArrayList<>(source);

        ordered.sort((first, second) -> {
            LocalDateTime firstTime = first.getReportTime();
            LocalDateTime secondTime = second.getReportTime();

            if (firstTime == null && secondTime == null) {
                return compareIds(first.getId(), second.getId());
            }
            if (firstTime == null) {
                return 1;
            }
            if (secondTime == null) {
                return -1;
            }

            int timeResult = firstTime.compareTo(secondTime);
            return timeResult != 0 ? timeResult : compareIds(first.getId(), second.getId());
        });

        GisPointFilterStatsVO stats = new GisPointFilterStatsVO();
        stats.setLoadedPointCount(ordered.size());

        int invalidCount = 0;
        int inaccurateCount = 0;
        int duplicateCount = 0;
        int speedOutlierCount = 0;

        Set<String> duplicateKeys = new HashSet<>();
        Map<Long, CleanPoint> previousBySession = new HashMap<>();
        List<CleanPoint> accepted = new ArrayList<>();

        for (ClientLocation point : ordered) {
            if (!isStructurallyValid(point)) {
                invalidCount++;
                continue;
            }

            if (point.getAccuracy().doubleValue() > maximumAccuracyMeters) {
                inaccurateCount++;
                continue;
            }

            String duplicateKey = buildDuplicateKey(point);
            if (!duplicateKeys.add(duplicateKey)) {
                duplicateCount++;
                continue;
            }

            CleanPoint previous = previousBySession.get(point.getSessionId());

            double distance = 0.0D;
            long elapsedSeconds = 0L;

            if (previous != null) {
                long elapsedMillis = Duration.between(previous.source.getReportTime(), point.getReportTime()).toMillis();

                if (elapsedMillis <= 0L) {
                    speedOutlierCount++;
                    continue;
                }

                double elapsed = elapsedMillis / 1000.0D;
                distance = GeoMath.distanceMeters(previous.source.getLatitude(), previous.source.getLongitude(), point.getLatitude(), point.getLongitude());
                double accuracyBuffer = previous.source.getAccuracy().doubleValue() + point.getAccuracy().doubleValue();

                double allowedDistance = maximumSpeedMetersPerSecond * elapsed + accuracyBuffer;

                if (distance > allowedDistance) {
                    speedOutlierCount++;
                    continue;
                }

                elapsedSeconds = Math.max(0L, elapsedMillis / 1000L);
            }

            CleanPoint acceptedPoint = new CleanPoint(point, distance, elapsedSeconds);

            accepted.add(acceptedPoint);
            previousBySession.put(point.getSessionId(), acceptedPoint);
        }

        stats.setInvalidPointCount(invalidCount);
        stats.setInaccuratePointCount(inaccurateCount);
        stats.setDuplicatePointCount(duplicateCount);
        stats.setSpeedOutlierPointCount(speedOutlierCount);
        stats.setUsedPointCount(accepted.size());

        ClientLocation identity = accepted.isEmpty() ? (ordered.isEmpty() ? null : ordered.get(0)) : accepted.get(0).source;

        return new CleanResult(accepted, stats, identity);
    }

    private List<GisStayPointResultVO.StayPoint> detectStayPoints(List<CleanPoint> points, int radiusMeters, long minimumStaySeconds) {

        List<GisStayPointResultVO.StayPoint> result = new ArrayList<>();

        int startIndex = 0;
        int sequence = 1;

        while (startIndex < points.size() - 1) {
            ClientLocation anchor = points.get(startIndex).source;
            int endExclusive = startIndex + 1;

            while (endExclusive < points.size()) {
                ClientLocation candidate = points.get(endExclusive).source;

                double distance = GeoMath.distanceMeters(anchor.getLatitude(), anchor.getLongitude(), candidate.getLatitude(), candidate.getLongitude());

                if (distance > radiusMeters) {
                    break;
                }

                endExclusive++;
            }

            int lastIncludedIndex = endExclusive - 1;
            ClientLocation last = points.get(lastIncludedIndex).source;

            long durationSeconds = Math.max(0L, Duration.between(anchor.getReportTime(), last.getReportTime()).getSeconds());

            if (lastIncludedIndex > startIndex && durationSeconds >= minimumStaySeconds) {

                GisStayPointResultVO.StayPoint stayPoint = createStayPoint(points, startIndex, lastIncludedIndex, sequence++);

                result.add(stayPoint);
                startIndex = endExclusive;
            } else {
                startIndex++;
            }
        }

        return result;
    }

    private GisStayPointResultVO.StayPoint createStayPoint(List<CleanPoint> points, int startIndex, int endIndex, int sequence) {

        double latitudeSum = 0.0D;
        double longitudeSum = 0.0D;

        for (int index = startIndex; index <= endIndex; index++) {
            ClientLocation point = points.get(index).source;
            latitudeSum += point.getLatitude().doubleValue();
            longitudeSum += point.getLongitude().doubleValue();
        }

        int pointCount = endIndex - startIndex + 1;
        BigDecimal centerLatitude = GeoMath.decimal(latitudeSum / pointCount, 7);
        BigDecimal centerLongitude = GeoMath.decimal(longitudeSum / pointCount, 7);

        double maximumDistance = 0.0D;

        for (int index = startIndex; index <= endIndex; index++) {
            ClientLocation point = points.get(index).source;

            maximumDistance = Math.max(maximumDistance, GeoMath.distanceMeters(centerLatitude, centerLongitude, point.getLatitude(), point.getLongitude()));
        }

        ClientLocation first = points.get(startIndex).source;
        ClientLocation last = points.get(endIndex).source;

        GisStayPointResultVO.StayPoint result = new GisStayPointResultVO.StayPoint();

        result.setSequence(sequence);
        result.setCenterLatitude(centerLatitude);
        result.setCenterLongitude(centerLongitude);
        result.setArrivalTime(first.getReportTime());
        result.setDepartureTime(last.getReportTime());
        result.setDurationSeconds(Duration.between(first.getReportTime(), last.getReportTime()).getSeconds());
        result.setPointCount(pointCount);
        result.setMaximumDistanceFromCenterMeters(GeoMath.decimal(maximumDistance, 2));

        GisStayPointResultVO.GeoJsonPoint geometry = new GisStayPointResultVO.GeoJsonPoint();

        geometry.setType("Point");
        geometry.setCoordinates(Arrays.asList(centerLongitude, centerLatitude));

        result.setGeometry(geometry);
        return result;
    }

    private List<GisHeatmapVO.HeatGrid> aggregateHeatmap(List<CleanPoint> points, int gridSizeMeters) {

        if (points.isEmpty()) {
            return new ArrayList<>();
        }

        double referenceLatitude = points.stream()
                .mapToDouble(point -> point.source.getLatitude().doubleValue())
                .average()
                .orElse(0.0D);

        double longitudeScale = GeoMath.metersPerLongitudeDegree(referenceLatitude);

        Map<String, GridAccumulator> accumulators = new LinkedHashMap<>();

        for (CleanPoint cleanPoint : points) {
            ClientLocation point = cleanPoint.source;

            long xIndex = (long) Math.floor(point.getLongitude().doubleValue() * longitudeScale / gridSizeMeters);

            long yIndex = (long) Math.floor(point.getLatitude().doubleValue() * GeoMath.METERS_PER_LATITUDE_DEGREE / gridSizeMeters);

            String key = xIndex + ":" + yIndex;

            GridAccumulator accumulator = accumulators.computeIfAbsent(key, ignored -> new GridAccumulator(key, xIndex, yIndex));

            accumulator.pointCount++;
        }

        int maximumCount = accumulators.values().stream()
                .mapToInt(value -> value.pointCount)
                .max()
                .orElse(1);

        List<GisHeatmapVO.HeatGrid> result = new ArrayList<>();

        for (GridAccumulator accumulator : accumulators.values()) {
            double minimumLongitude = accumulator.xIndex * gridSizeMeters / longitudeScale;
            double maximumLongitude = (accumulator.xIndex + 1L) * gridSizeMeters / longitudeScale;
            double minimumLatitude = accumulator.yIndex * gridSizeMeters / GeoMath.METERS_PER_LATITUDE_DEGREE;
            double maximumLatitude = (accumulator.yIndex + 1L) * gridSizeMeters / GeoMath.METERS_PER_LATITUDE_DEGREE;

            minimumLatitude = clamp(minimumLatitude, -90.0D, 90.0D);
            maximumLatitude = clamp(maximumLatitude, -90.0D, 90.0D);
            minimumLongitude = clamp(minimumLongitude, -180.0D, 180.0D);
            maximumLongitude = clamp(maximumLongitude, -180.0D, 180.0D);

            result.add(createHeatGrid(accumulator, minimumLatitude, maximumLatitude, minimumLongitude, maximumLongitude, maximumCount));
        }

        result.sort(Comparator.comparing(GisHeatmapVO.HeatGrid::getPointCount).reversed().thenComparing(GisHeatmapVO.HeatGrid::getGridKey));

        return result;
    }

    private GisHeatmapVO.HeatGrid createHeatGrid(GridAccumulator accumulator, double minimumLatitude, double maximumLatitude, double minimumLongitude, double maximumLongitude, int maximumCount) {

        BigDecimal minLatitude = GeoMath.decimal(minimumLatitude, 7);
        BigDecimal maxLatitude = GeoMath.decimal(maximumLatitude, 7);
        BigDecimal minLongitude = GeoMath.decimal(minimumLongitude, 7);
        BigDecimal maxLongitude = GeoMath.decimal(maximumLongitude, 7);

        GisHeatmapVO.HeatGrid grid = new GisHeatmapVO.HeatGrid();

        grid.setGridKey(accumulator.key);
        grid.setMinimumLatitude(minLatitude);
        grid.setMaximumLatitude(maxLatitude);
        grid.setMinimumLongitude(minLongitude);
        grid.setMaximumLongitude(maxLongitude);
        grid.setCenterLatitude(GeoMath.decimal((minimumLatitude + maximumLatitude) / 2.0D, 7));
        grid.setCenterLongitude(GeoMath.decimal((minimumLongitude + maximumLongitude) / 2.0D, 7));
        grid.setPointCount(accumulator.pointCount);
        grid.setWeight(GeoMath.decimal((double) accumulator.pointCount / maximumCount, 4));

        List<List<BigDecimal>> ring = Arrays.asList(
                Arrays.asList(minLongitude, minLatitude),
                Arrays.asList(maxLongitude, minLatitude),
                Arrays.asList(maxLongitude, maxLatitude),
                Arrays.asList(minLongitude, maxLatitude),
                Arrays.asList(minLongitude, minLatitude)
        );

        GisHeatmapVO.GeoJsonPolygon geometry = new GisHeatmapVO.GeoJsonPolygon();

        geometry.setType("Polygon");
        geometry.setCoordinates(Collections.singletonList(ring));

        grid.setGeometry(geometry);
        return grid;
    }

    private boolean isStructurallyValid(ClientLocation point) {
        if (point == null
                || point.getId() == null
                || point.getSessionId() == null
                || point.getSessionId() <= 0
                || point.getReportTime() == null
                || point.getLatitude() == null
                || point.getLongitude() == null
                || point.getAccuracy() == null
                || !Integer.valueOf(1).equals(point.getTrustedBinding())) {
            return false;
        }

        return point.getLatitude().compareTo(new BigDecimal("-90")) >= 0 &&
                point.getLatitude().compareTo(new BigDecimal("90")) <= 0 &&
                point.getLongitude().compareTo(new BigDecimal("-180")) >= 0 &&
                point.getLongitude().compareTo(new BigDecimal("180")) <= 0 &&
                point.getAccuracy().signum() >= 0;
    }

    private String buildDuplicateKey(ClientLocation point) {
        return point.getSessionId()
                + "|" + point.getReportTime()
                + "|" + point.getLatitude()
                .stripTrailingZeros().toPlainString()
                + "|" + point.getLongitude()
                .stripTrailingZeros().toPlainString();
    }

    private long calculateDuration(List<CleanPoint> points) {
        if (points.size() < 2) {
            return 0L;
        }

        return Math.max(0L, Duration.between(points.get(0).source.getReportTime(), points.get(points.size() - 1).source.getReportTime()).getSeconds());
    }

    private void validateQuery(LocalDateTime startTime, LocalDateTime endTime, Double maximumAccuracyMeters) {
        validateConfiguration();
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("时间范围无效");
        }

        if (Duration.between(startTime, endTime).compareTo(Duration.ofDays(maximumQueryDays)) > 0) {
            throw new IllegalArgumentException("GIS查询时间范围不能超过" + maximumQueryDays + "天");
        }

        if (maximumAccuracyMeters == null
                || !Double.isFinite(maximumAccuracyMeters)
                || maximumAccuracyMeters < 1.0D
                || maximumAccuracyMeters > 1000.0D) {
            throw new IllegalArgumentException("最大定位误差必须在1到1000米之间");
        }
    }

    private void validateConfiguration() {
        if (maximumQueryPoints < 100 || maximumQueryPoints > 50000) {
            throw new IllegalStateException("GIS最大查询点数配置无效");
        }

        if (maximumQueryDays < 1 || maximumQueryDays > 31) {
            throw new IllegalStateException("GIS最大查询天数配置无效");
        }

        if (!Double.isFinite(maximumSpeedMetersPerSecond) || maximumSpeedMetersPerSecond <= 0.0D || maximumSpeedMetersPerSecond > 1000.0D) {
            throw new IllegalStateException("GIS最大移动速度配置无效");
        }
    }

    private void requireSessionId(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException("sessionId无效");
        }
    }

    private String normalizeOptionalMac(String mac) {
        if (!StringUtils.hasText(mac)) {
            return null;
        }

        String normalized = mac.trim().toUpperCase(Locale.ROOT);

        if (!MAC_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("MAC格式不正确");
        }

        return normalized;
    }

    private int compareIds(Long first, Long second) {
        if (first == null && second == null) {
            return 0;
        }
        if (first == null) {
            return 1;
        }
        if (second == null) {
            return -1;
        }
        return first.compareTo(second);
    }

    private static class CleanPoint {
        private final ClientLocation source;
        private final double distanceFromPreviousMeters;
        private final long elapsedSeconds;

        private CleanPoint(ClientLocation source, double distanceFromPreviousMeters, long elapsedSeconds) {
            this.source = source;
            this.distanceFromPreviousMeters = distanceFromPreviousMeters;
            this.elapsedSeconds = elapsedSeconds;
        }
    }

    private static class CleanResult {
        private final List<CleanPoint> points;
        private final GisPointFilterStatsVO stats;
        private final ClientLocation identity;

        private CleanResult(List<CleanPoint> points, GisPointFilterStatsVO stats, ClientLocation identity) {
            this.points = points;
            this.stats = stats;
            this.identity = identity;
        }
    }

    private static class GridAccumulator {
        private final String key;
        private final long xIndex;
        private final long yIndex;
        private int pointCount;

        private GridAccumulator(String key, long xIndex, long yIndex) {
            this.key = key;
            this.xIndex = xIndex;
            this.yIndex = yIndex;
        }
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}