package com.plagod.dto.entitlement;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class RefundApplyRequest {

    @NotBlank
    @Size(max = 56)
    private String requestId;

    @NotBlank
    @Size(max = 64)
    private String purchaseId;

    @NotBlank
    @Size(max = 255)
    private String reason;
}
