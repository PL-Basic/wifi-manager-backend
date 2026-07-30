package com.plagod.vo.monitor;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class GisStayPointResultVO {

    private String coordinateSystem;
    private Long sessionId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer radiusMeters;
    private Long minimumStaySeconds;
    private GisPointFilterStatsVO filterStats;
    private List<StayPoint> stayPoints;

    @Data
    public static class StayPoint {
        private Integer sequence;
        private BigDecimal centerLatitude;
        private BigDecimal centerLongitude;
        private LocalDateTime arrivalTime;
        private LocalDateTime departureTime;
        private Long durationSeconds;
        private Integer pointCount;
        private BigDecimal maximumDistanceFromCenterMeters;
        private GeoJsonPoint geometry;
    }

    @Data
    public static class GeoJsonPoint {
        private String type;
        private List<BigDecimal> coordinates;
    }
}