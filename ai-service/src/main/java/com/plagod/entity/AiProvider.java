package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_ai_provider")
public class AiProvider {

    @TableId(type = IdType.AUTO)
    private Long providerId;
    private String providerCode;
    private String displayName;
    private String adapterType;
    private String modelIdentifier;
    private String status;
    private String configReference;
    private Integer noTrainingSupported;
    private String retentionMode;
    private Integer timeoutMs;
    private LocalDateTime lastVerifiedTime;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
