package com.plagod.dto.entitlement;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class UnlimitedEntitlementRequest {

    @NotBlank
    @Size(max = 56)
    private String requestId;

    @NotBlank
    @Pattern(regexp = "GRANT|REVOKE")
    private String action;

    @NotBlank
    @Size(max = 255)
    private String reason;
}
