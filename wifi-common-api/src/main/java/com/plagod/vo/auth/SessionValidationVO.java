package com.plagod.vo.auth;

import lombok.Data;

@Data
public class SessionValidationVO {

    private Boolean active;
    private String userId;
    private String sessionId;
    private String status;
    private String reason;
    private String contextType;
    private String tenantId;
    private Long contextVersion;
    private Long memberContextVersion;
    private Long securityVersion;
}
