package com.plagod.entity.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_oauth_state")
public class OAuthStateRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "state_id", type = IdType.AUTO)
    private Long stateId;

    private String stateHash;
    private String provider;
    private String purpose;
    private Long bindUserId;
    private String returnUri;

    private String authorizationCodeHash;
    private Integer status;
    private String resultStatus;
    private Long resultUserId;
    private String resultMessage;

    private LocalDateTime expireTime;
    private LocalDateTime consumeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}