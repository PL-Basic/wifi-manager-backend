package com.plagod.entity.entitlement;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_entitlement_lease_receipt")
public class EntitlementLeaseReceipt {

    @TableId(type = IdType.AUTO)
    private Long receiptId;
    private Long tenantId;
    private String requestId;
    private String requestFingerprint;
    private Long entitlementId;
    private Long userId;
    private Long sessionId;
    private Long usageSeconds;
    private Integer requestedTtlSeconds;
    private String receiptStatus;
    private Boolean resultAllowed;
    private Long resultEntitlementId;
    private String resultMode;
    private Integer resultTtlSeconds;
    private Long resultChargedSeconds;
    private Long resultRemainingSeconds;
    private LocalDateTime resultSubscriptionEndTime;
    private String resultReason;
    private LocalDateTime completedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
