package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_tenant_quota_reservation")
public class TenantQuotaReservation {
    @TableId(type = IdType.AUTO)
    private Long reservationId;
    private Long tenantId;
    private String quotaType;
    private String businessType;
    private String businessKey;
    private String eventId;
    private Long amount;
    private String status;
    private LocalDateTime expireTime;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
