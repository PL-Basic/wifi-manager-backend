package com.plagod.vo.monitor;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LocationAuthorizationVO {

    private Long userId;
    private Integer enabled;
    private LocalDateTime consentTime;
    private LocalDateTime revokedTime;
    private LocalDateTime lastReportTime;
}