package com.plagod.dto;

import lombok.Data;

@Data
public class OAuthProfile {

    private String provider;
    private String providerSubject;
    private String providerUnionId;
    private String providerUsername;
    private String displayName;
    private String avatarUrl;
    private String verifiedEmail;
    private Boolean emailVerified;
}