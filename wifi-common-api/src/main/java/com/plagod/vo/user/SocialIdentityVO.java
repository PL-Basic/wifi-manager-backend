package com.plagod.vo.user;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SocialIdentityVO {

    private Long identityId;
    private Long userId;
    private String provider;
    private String providerUsername;
    private String displayName;
    private String avatarUrl;
    private String email;
    private Boolean emailVerified;
    private LocalDateTime bindTime;
    private LocalDateTime lastLoginTime;
}