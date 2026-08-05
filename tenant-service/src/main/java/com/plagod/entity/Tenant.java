package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_tenant")
public class Tenant {
    @TableId(type = IdType.AUTO)
    private Long tenantId;
    private String tenantCode;
    private String name;
    private String status;
    private String timezone;
    private Long ownerUserId;
    private Long contextVersion;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;
}
