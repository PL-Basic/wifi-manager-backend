package com.plagod.vo.device;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SignalAnalyticsSourceVO {

    private Long nodeId;
    private String deviceCode;
    private String mac;
    private Long sessionId;

    private BigDecimal nodeLatitude;
    private BigDecimal nodeLongitude;
    private Integer rssiAtOneMeter;
    private BigDecimal pathLossExponent;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer sampleLimit;
    private List<SignalSample> latestSamples;
    private List<SignalTrendBucket> trend;

    @Data
    public static class SignalSample {
        private Long id;
        private Long sessionId;
        private Integer rssi;
        private LocalDateTime reportTime;
    }

    @Data
    public static class SignalTrendBucket {
        private LocalDateTime bucketTime;
        private Long sampleCount;
        private BigDecimal averageRssi;
        private Integer minRssi;
        private Integer maxRssi;
    }
}