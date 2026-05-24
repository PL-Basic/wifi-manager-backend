package com.plagod.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrafficLogVO {
    private Long id;
    private Long sessionId;
    private String mac;
    private String dstIp;
    private Integer dstPort;
    private String sni;
    private String protocol;
    private Long bytesUp;
    private Long bytesDown;
    private LocalDateTime logTime;
}
