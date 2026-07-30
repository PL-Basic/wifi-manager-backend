package com.plagod.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OAuthAuthorizationVO {

    private String provider;
    private String purpose;
    private String authorizationUrl;
    private LocalDateTime expireTime;
}