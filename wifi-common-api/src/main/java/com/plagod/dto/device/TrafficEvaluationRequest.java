package com.plagod.dto.device;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrafficEvaluationRequest {

    private String eventId;
    private String deviceCode;
    private Long nodeId;
    private Long sessionId;
    private Long userId;
    private String mac;
    private String dstIp;
    private Integer dstPort;
    private String sni;
    private String protocol;
    private LocalDateTime eventTime;
}