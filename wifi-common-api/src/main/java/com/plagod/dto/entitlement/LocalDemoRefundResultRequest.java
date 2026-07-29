package com.plagod.dto.entitlement;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class LocalDemoRefundResultRequest {

    @NotBlank
    @Size(max = 56)
    private String requestId;

    @NotNull
    private Boolean success;

    @Size(max = 255)
    private String failureMessage;
}