package com.plagod.vo.monitor;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class GisHeatmapVO {

    private String coordinateSystem;
    private Long userId;
    private Long sessionId;
    private Long nodeId;
    private String mac;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer gridSizeMeters;
    private Integer totalAggregatedPointCount;
    private Integer maximumCellPointCount;
    private GisPointFilterStatsVO filterStats;
    private List<HeatGrid> grids;

    @Data
    public static class HeatGrid {
        private String gridKey;
        private BigDecimal centerLatitude;
        private BigDecimal centerLongitude;
        private BigDecimal minimumLatitude;
        private BigDecimal maximumLatitude;
        private BigDecimal minimumLongitude;
        private BigDecimal maximumLongitude;
        private Integer pointCount;
        private BigDecimal weight;
        private GeoJsonPolygon geometry;
    }

    @Data
    public static class GeoJsonPolygon {
        private String type;
        private List<List<List<BigDecimal>>> coordinates;
    }
}