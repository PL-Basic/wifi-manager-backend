package com.plagod.vo.tenant;

import lombok.Data;

@Data
public class TenantContextValidationVO {

    private Boolean allowed;
    private String denialCode;
    private String message;
    private TenantContextVO context;
}
