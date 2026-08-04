package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_saas_plan")
public class SaasPlan {
    @TableId(type = IdType.AUTO)
    private Long planId;
    private String planCode;
    private String name;
    private String status;
    private Long currentPublishedVersionId;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
