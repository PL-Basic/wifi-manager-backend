package com.plagod.vo.monitor;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class GisTrajectoryVO {

    private String coordinateSystem;
    private Long sessionId;
    private Long userId;
    private Long nodeId;
    private String deviceCode;
    private String mac;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationSeconds;
    private BigDecimal totalDistanceMeters;
    private GisPointFilterStatsVO filterStats;
    private List<TrajectoryPoint> points;
    private GeoJsonLineString geometry;

    @Data
    public static class TrajectoryPoint {
        private Long locationId;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private BigDecimal accuracy;
        private LocalDateTime reportTime;
        private Long elapsedSeconds;
        private BigDecimal distanceFromPreviousMeters;
    }

    @Data
    public static class GeoJsonLineString {
        private String type;
        private List<List<BigDecimal>> coordinates;
    }
}