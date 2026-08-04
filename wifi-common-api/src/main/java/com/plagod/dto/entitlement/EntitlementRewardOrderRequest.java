package com.plagod.dto.entitlement;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

@Data
public class EntitlementRewardOrderRequest {

    @NotBlank
    @Size(max = 56)
    private String requestId;

    @NotBlank
    @Pattern(regexp = "DURATION|SUBSCRIPTION")
    private String mode;

    @NotNull
    @Positive
    private Long grantSeconds;

    @NotNull
    @PositiveOrZero
    private Long amountCents;

    @NotBlank
    @Size(max = 255)
    private String reason;
}
