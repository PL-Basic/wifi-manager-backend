package com.plagod.vo.tenant;

import lombok.Data;

@Data
public class MyTenantVO {
    private String tenantId;
    private String tenantCode;
    private String tenantName;
    private String tenantStatus;
    private String timezone;
    private String tenantRole;
    private Boolean defaultTenant;
    private Long tenantContextVersion;
    private Long memberContextVersion;
}
