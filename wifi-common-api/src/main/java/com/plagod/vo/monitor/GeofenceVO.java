package com.plagod.vo.monitor;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class GeofenceVO {

    private Long fenceId;
    private String name;
    private String coordinateSystem;
    private BigDecimal centerLatitude;
    private BigDecimal centerLongitude;
    private BigDecimal radiusMeters;
    private Integer enabled;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private GeoJsonPoint geometry;

    @Data
    public static class GeoJsonPoint {
        private String type;

        // GeoJSON固定使用[longitude, latitude]。
        private List<BigDecimal> coordinates;
    }
}