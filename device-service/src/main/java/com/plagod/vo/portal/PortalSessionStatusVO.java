package com.plagod.vo.portal;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PortalSessionStatusVO {

    private Long sessionId;

    private Integer sessionStatusCode;
    private String sessionStatus;
    private String statusMessage;

    private String deviceCode;
    private String hotspotName;

    private String authorizationMode;
    private Integer entitlementStatus;
    private LocalDateTime leaseExpireTime;
    private Long remainingSeconds;
    private LocalDateTime subscriptionEndTime;

    private Long replacedSessionId;
    private String endReason;

    private String commandRequestId;
    private String commandType;
    private String commandPurpose;
    private Integer commandStatusCode;
    private String commandStatus;
    private String commandResultMessage;
    private LocalDateTime commandPublishTime;
    private LocalDateTime commandResultTime;
}