package com.plagod.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class UserAccountCreateRequest {

    @NotBlank
    private String idempotencyKey;

    @NotBlank
    private String requestFingerprint;

    @NotBlank
    private String username;

    @NotBlank
    private String passwordHash;

    @NotBlank
    private String nickname;

    private String email;

    private String phone;
}
