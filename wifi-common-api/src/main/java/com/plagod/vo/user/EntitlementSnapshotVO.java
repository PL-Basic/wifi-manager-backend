package com.plagod.vo.user;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EntitlementSnapshotVO {

    private Long entitlementId;
    private Long userId;
    private String mode;
    private LocalDateTime subscriptionStartTime;
    private LocalDateTime subscriptionEndTime;
    private Long remainingSeconds;
    private Integer status;
}