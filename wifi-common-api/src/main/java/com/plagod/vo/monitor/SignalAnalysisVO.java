package com.plagod.vo.monitor;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SignalAnalysisVO {

    private Long nodeId;
    private String deviceCode;
    private String mac;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime latestReportTime;

    private Integer rssiAtOneMeter;
    private BigDecimal pathLossExponent;

    private Integer rawSampleCount;
    private Integer invalidSampleCount;
    private Integer outlierSampleCount;
    private Integer usedSampleCount;

    private BigDecimal smoothedRssi;
    private Integer rssiUncertaintyDb;
    private String qualityLevel;
    private String qualityDescription;

    private BigDecimal estimatedDistanceMeters;
    private BigDecimal minimumDistanceMeters;
    private BigDecimal maximumDistanceMeters;
    private String confidenceLevel;
    private String confidenceDescription;

    private String filterMethod;
    private String smoothingMethod;
    private String positioningCapability;
    private String limitation;

    private List<SignalTrendPoint> trend;

    @Data
    public static class SignalTrendPoint {
        private LocalDateTime bucketTime;
        private Long sampleCount;
        private BigDecimal averageRssi;
        private Integer minRssi;
        private Integer maxRssi;
        private String qualityLevel;
        private BigDecimal estimatedDistanceMeters;
    }
}