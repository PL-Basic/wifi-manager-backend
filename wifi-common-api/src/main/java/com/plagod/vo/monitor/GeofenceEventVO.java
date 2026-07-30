package com.plagod.vo.monitor;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class GeofenceEventVO {

    private Long eventId;
    private Long fenceId;
    private String fenceName;
    private Long locationId;
    private Long userId;
    private Long sessionId;
    private Long nodeId;
    private String deviceCode;
    private String mac;
    private String eventType;
    private LocalDateTime eventTime;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String coordinateSystem;
    private GeoJsonPoint geometry;

    @Data
    public static class GeoJsonPoint {
        private String type;
        private List<BigDecimal> coordinates;
    }
}