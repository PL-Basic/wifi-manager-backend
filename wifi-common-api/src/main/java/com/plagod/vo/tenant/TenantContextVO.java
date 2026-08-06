package com.plagod.vo.tenant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TenantContextVO {

    private String contextType;
    private String tenantId;
    private String tenantCode;
    private String tenantName;
    private String tenantRole;
    private Long contextVersion;
    private Long memberContextVersion;
    private String tenantStatus;
    private Boolean writable;
    private List<String> authorities = new ArrayList<>();
}
