package com.plagod.vo.device;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceCommandVO {

    private Long commandId;
    private String requestId;
    private Long nodeId;
    private String deviceCode;
    private String commandType;
    private String purpose;

    private Long sessionId;
    private String mac;
    private Long alertId;
    private Integer ttlSeconds;

    private String topic;
    private String payload;

    private Integer status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private LocalDateTime publishTime;
    private LocalDateTime deadlineTime;
    private LocalDateTime resultTime;
    private String resultMessage;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}