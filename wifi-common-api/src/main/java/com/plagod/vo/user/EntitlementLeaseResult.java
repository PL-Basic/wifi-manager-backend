package com.plagod.vo.user;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EntitlementLeaseResult {
    // false 表示不得继续向固件发布 ALLOW
    private Boolean allowed;
    // true 表示返回的是同一 requestId 第一次执行的结果
    private Boolean duplicate;
    private Long entitlementId;
    private String mode;
    // 不允许续租时必须为 null，绝不能把 0 发送给 ESP32
    private Integer ttlSeconds;
    // 本次实际扣除的购买时长；订阅模式为 0
    private Long chargedSeconds;
    private Long remainingSeconds;
    private LocalDateTime subscriptionEndTime;
    private String reason;
}