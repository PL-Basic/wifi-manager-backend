package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_tenant_creation_receipt")
public class TenantCreationReceipt {

    @TableId(type = IdType.AUTO)
    private Long creationReceiptId;
    private Long platformActorId;
    private String clientRequestId;
    private String requestFingerprint;
    private Long targetTenantId;
    private String receiptStatus;
    private String resultCode;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
