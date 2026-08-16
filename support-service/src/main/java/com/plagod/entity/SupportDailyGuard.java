package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_support_daily_guard")
public class SupportDailyGuard {

    @TableId(type = IdType.AUTO)
    private Long dailyGuardId;
    private Long tenantId;
    private Long userId;
    private LocalDate quotaDate;
    private Integer limitUsed;
    private Integer attemptCount;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
