package com.plagod.vo.auth;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperationTokenVO {

    private String token;
    private String purpose;
    private LocalDateTime expiresAt;
}
