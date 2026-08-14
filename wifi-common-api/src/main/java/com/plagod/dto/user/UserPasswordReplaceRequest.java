package com.plagod.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class UserPasswordReplaceRequest {

    @NotBlank
    private String idempotencyKey;

    @NotBlank
    private String requestFingerprint;

    @NotNull
    private Long userId;

    @NotBlank
    private String expectedPasswordHash;

    @NotBlank
    private String newPasswordHash;
}
