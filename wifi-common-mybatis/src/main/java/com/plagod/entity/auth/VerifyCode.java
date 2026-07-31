package com.plagod.entity.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_verify_code")
public class VerifyCode {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String target;
    private String targetType;
    private String scene;

    private String codeHash;
    private String verificationProvider;
    private String providerOutId;
    private String providerBizId;
    private String providerRequestId;
    private String providerSendCode;

    private Integer sendStatus;
    private LocalDateTime sendTime;
    private String sendError;

    private Integer verifyStatus;
    private Integer verifyAttemptCount;
    private String providerVerifyCode;
    private String providerVerifyResult;
    private String verifyError;

    private Integer status;
    private LocalDateTime expireTime;
    private LocalDateTime verifyTime;
    private LocalDateTime consumeTime;
    private String sendIp;
    private String verifyIp;
    private LocalDateTime createTime;
}