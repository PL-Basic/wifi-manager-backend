package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_tenant_usage_daily")
public class TenantUsageDaily {
    @TableId(type = IdType.AUTO)
    private Long usageId;
    private Long tenantId;
    private LocalDate usageDate;
    private String usageType;
    private Long usedValue;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
