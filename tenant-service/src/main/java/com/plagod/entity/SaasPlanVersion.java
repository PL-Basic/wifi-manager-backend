package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_saas_plan_version")
public class SaasPlanVersion {
    @TableId(type = IdType.AUTO)
    private Long planVersionId;
    private Long planId;
    private Integer versionNo;
    private Long memberLimit;
    private Long deviceLimit;
    private Long concurrentSessionLimit;
    private Long dailyMinutesLimit;
    private String status;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
}
