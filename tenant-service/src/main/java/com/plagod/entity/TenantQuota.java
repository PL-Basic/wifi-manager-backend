package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_tenant_quota")
public class TenantQuota {
    @TableId(type = IdType.AUTO)
    private Long tenantQuotaId;
    private Long tenantId;
    private String quotaType;
    private Long limitValue;
    private Long usedValue;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
