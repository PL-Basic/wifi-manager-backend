package com.plagod.dto.entitlement;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class RefundReviewRequest {

    @NotBlank
    @Pattern(regexp = "APPROVE|REJECT")
    private String decision;

    @Size(max = 255)
    private String comment;
}