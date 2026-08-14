package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_support_user_guard")
public class SupportUserGuard {

    @TableId(type = IdType.AUTO)
    private Long userGuardId;
    private Long tenantId;
    private Long userId;
    private Integer pendingCount;
    private Integer unresolvedCount;
    private Integer abuseWarningCount;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
