package com.plagod.dto.entitlement;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class LocalDemoPaymentCallbackRequest {

    @NotBlank
    @Size(max = 64)
    private String businessKey;

    @NotBlank
    @Size(max = 64)
    private String eventId;

    @NotBlank
    @Size(max = 64)
    private String channelTransactionNo;

    @NotNull
    @Min(1)
    private Long paidAmountCents;

    @NotNull
    @Min(1)
    private Long timestamp;

    @NotBlank
    @Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "回调签名格式错误")
    private String signature;
}