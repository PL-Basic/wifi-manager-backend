package com.plagod.entity.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_auth_refresh_session")
public class AuthRefreshSession {

    @TableId(type = IdType.INPUT)
    private String sessionId;
    private Long userId;
    private String clientInstanceId;
    private String contextType;
    private Long tenantId;
    private String tenantCode;
    private String tenantRole;
    private Long tenantContextVersion;
    private Long memberContextVersion;
    private Long securityVersion;
    private String authoritiesJson;
    private String status;
    private LocalDateTime absoluteExpiresAt;
    private String currentTokenId;
    private String userAgentHash;
    private String initialIpNetworkHash;
    private String lastIpNetworkHash;
    private Integer ipChangeCount;
    private Integer userAgentChangeCount;
    private Integer stepUpRequired;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
