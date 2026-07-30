package com.plagod.vo.monitor;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class GisNodeCoverageVO {

    private String coordinateSystem;
    private String analysisPurpose;
    private String positioningCapability;
    private String limitation;

    private Long sessionId;
    private Long userId;
    private Long nodeId;
    private String deviceCode;
    private String mac;

    private BigDecimal nodeLatitude;
    private BigDecimal nodeLongitude;
    private Integer rssiAtOneMeter;
    private BigDecimal pathLossExponent;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Double maximumAccuracyMeters;
    private Integer matchToleranceSeconds;

    private Integer gpsPointCount;
    private Integer rssiSampleCount;
    private Integer ignoredRssiSampleCount;
    private Integer matchedPointCount;
    private Integer unmatchedGpsPointCount;
    private Integer unusedRssiSampleCount;

    private BigDecimal maximumObservedDistanceMeters;
    private BigDecimal averageAbsoluteErrorMeters;
    private BigDecimal averageErrorRatio;

    private List<CoverageObservation> observations;

    @Data
    public static class CoverageObservation {

        private Long locationId;
        private Long signalId;

        private BigDecimal latitude;
        private BigDecimal longitude;
        private BigDecimal accuracy;

        private LocalDateTime locationReportTime;
        private LocalDateTime signalReportTime;
        private Long timeDifferenceMillis;
        private Integer rssi;

        private BigDecimal actualDistanceMeters;
        private BigDecimal estimatedDistanceMeters;
        private BigDecimal absoluteErrorMeters;
        private BigDecimal errorRatio;

        private String confidenceLevel;
        private String confidenceDescription;
        private GeoJsonPoint geometry;
    }

    @Data
    public static class GeoJsonPoint {
        private String type;
        private List<BigDecimal> coordinates;
    }
}