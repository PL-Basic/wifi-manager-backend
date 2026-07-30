package com.plagod.vo.device;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TrafficAnalyticsSourceVO {

    private Long userId;
    private String mac;
    private Long sessionId;
    private Long nodeId;
    private String deviceCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer bucketMinutes;
    private Integer topLimit;

    private Summary summary;
    private List<TimeBucket> trend;
    private List<RankBucket> users;
    private List<RankBucket> macs;
    private List<RankBucket> sessions;
    private List<RankBucket> nodes;
    private List<RankBucket> devices;
    private List<RankBucket> destinationIps;
    private List<RankBucket> destinationPorts;
    private List<RankBucket> snis;
    private List<RankBucket> protocols;

    @Data
    public static class Summary {
        private Long eventCount;
        private Long bytesUp;
        private Long bytesDown;
        private Long totalBytes;
        private Long distinctUsers;
        private Long distinctMacs;
        private Long distinctSessions;
        private Long distinctNodes;
        private Long distinctDevices;
    }

    @Data
    public static class TimeBucket {
        private LocalDateTime bucketTime;
        private Long eventCount;
        private Long bytesUp;
        private Long bytesDown;
        private Long totalBytes;
    }

    @Data
    public static class RankBucket {
        private String dimensionKey;
        private Long eventCount;
        private Long bytesUp;
        private Long bytesDown;
        private Long totalBytes;
    }
}