package com.plagod.service;

import com.plagod.vo.tenant.TenantContextVO;
import lombok.Data;

@Data
public class GatewayIdentityContext {

    private Long userId;
    private String username;
    private Integer role;
    private String sessionId;
    private String tokenId;
    private boolean legacyToken;
    private TenantContextVO tenantContext;
}
