package com.plagod.entity.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_auth_refresh_token")
public class AuthRefreshToken {

    @TableId(type = IdType.INPUT)
    private String tokenId;
    private String sessionId;
    private String tokenHash;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime consumedAt;
    private LocalDateTime expiresAt;
    private String replacedByTokenId;
    private String userAgentHash;
    private String ipNetworkHash;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
