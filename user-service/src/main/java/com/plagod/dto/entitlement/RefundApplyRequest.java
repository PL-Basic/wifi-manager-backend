package com.plagod.dto.entitlement;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

@Data
public class RefundApplyRequest {

    @NotBlank
    @Size(max = 56)
    private String requestId;

    @NotNull
    @Positive
    private Long purchaseId;

    @NotBlank
    @Size(max = 255)
    private String reason;
}