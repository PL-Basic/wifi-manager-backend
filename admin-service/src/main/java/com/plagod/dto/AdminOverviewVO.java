package com.plagod.dto;

import lombok.Data;

@Data
public class AdminOverviewVO {
    private String gatewayStatus;
    private String userServiceStatus;
    private String deviceServiceStatus;
    private String monitorServiceStatus;
}
