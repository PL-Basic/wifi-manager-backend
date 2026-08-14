package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_capability_restriction")
public class UserCapabilityRestriction {

    @TableId(type = IdType.AUTO)
    private Long restrictionId;
    private String scopeType;
    private Long tenantId;
    @TableField(
            insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private String scopeKey;
    private Long userId;
    private String capability;
    private String status;
    private String reasonCode;
    private Long operatorUserId;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
