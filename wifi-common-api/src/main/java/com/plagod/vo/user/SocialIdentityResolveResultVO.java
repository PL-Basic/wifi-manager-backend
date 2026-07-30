package com.plagod.vo.user;

import com.plagod.constant.SocialIdentityResolveStatus;
import lombok.Data;

@Data
public class SocialIdentityResolveResultVO {

    private SocialIdentityResolveStatus status;
    private String message;
    private SocialLoginPrincipalVO principal;
    private SocialIdentityVO identity;

    public static SocialIdentityResolveResultVO loginReady(SocialLoginPrincipalVO principal, SocialIdentityVO identity) {
        SocialIdentityResolveResultVO result = new SocialIdentityResolveResultVO();
        result.setStatus(SocialIdentityResolveStatus.LOGIN_READY);
        result.setPrincipal(principal);
        result.setIdentity(identity);
        return result;
    }

    public static SocialIdentityResolveResultVO bindReady(SocialLoginPrincipalVO principal, SocialIdentityVO identity) {
        SocialIdentityResolveResultVO result = new SocialIdentityResolveResultVO();
        result.setStatus(SocialIdentityResolveStatus.BIND_READY);
        result.setPrincipal(principal);
        result.setIdentity(identity);
        return result;
    }

    public static SocialIdentityResolveResultVO bindRequired(String message) {
        SocialIdentityResolveResultVO result = new SocialIdentityResolveResultVO();
        result.setStatus(SocialIdentityResolveStatus.BIND_REQUIRED);
        result.setMessage(message);
        return result;
    }
}