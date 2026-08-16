package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_ai_policy")
public class AiPolicy {

    @TableId(type = IdType.AUTO)
    private Long policyId;
    private String policyCode;
    private String scene;
    private String displayName;
    private String status;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
