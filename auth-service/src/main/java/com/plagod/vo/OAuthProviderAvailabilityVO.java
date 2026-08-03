package com.plagod.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OAuthProviderAvailabilityVO {
    private String provider;
    private String displayName;
    private boolean configured;
}
