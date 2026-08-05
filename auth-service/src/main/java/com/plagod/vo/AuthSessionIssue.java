package com.plagod.vo;

import com.plagod.dto.auth.AuthResultDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

@Getter
@AllArgsConstructor
public class AuthSessionIssue {

    private final AuthResultDTO authResult;
    private final String refreshToken;
    private final Duration cookieMaxAge;
    private final String sessionId;
}
