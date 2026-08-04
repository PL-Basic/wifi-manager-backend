package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_tenant_member")
public class TenantMember {
    @TableId(type = IdType.AUTO)
    private Long memberId;
    private Long tenantId;
    private Long userId;
    private String tenantRole;
    private String status;
    private Integer isDefault;
    @TableField(exist = false)
    private Long activeDefaultUserGuard;
    private Long contextVersion;
    private LocalDateTime joinTime;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
