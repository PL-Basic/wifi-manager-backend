package com.plagod.dto.device;

import lombok.Data;

@Data
public class TrafficEvaluationRequest {
    private String mac;
    private Long sessionId;
    private Long userId;
    private String dstIp;
    private Integer dstPort;
    private String sni;
    private String protocol;
}
