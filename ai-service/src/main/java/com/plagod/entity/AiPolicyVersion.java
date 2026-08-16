package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_ai_policy_version")
public class AiPolicyVersion {

    @TableId(type = IdType.AUTO)
    private Long policyVersionId;
    private Long policyId;
    private Integer versionNo;
    private String instructionReference;
    private String instructionHash;
    private String responseSchemaReference;
    private String responseSchemaHash;
    private Integer approveThresholdBps;
    private Integer manualThresholdBps;
    private String status;
    @TableField(
            insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private Long activePolicyKey;
    private LocalDateTime publishTime;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
