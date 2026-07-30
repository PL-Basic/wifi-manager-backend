package com.plagod.service;

import com.plagod.dto.user.SocialIdentityResolveDTO;
import com.plagod.vo.user.SocialIdentityResolveResultVO;
import com.plagod.vo.user.SocialIdentityVO;
import com.plagod.vo.user.SocialLoginPrincipalVO;

import java.util.List;

public interface SocialIdentityService {

    SocialIdentityResolveResultVO resolve(SocialIdentityResolveDTO resolveDTO);

    SocialLoginPrincipalVO getLoginPrincipal(Long userId);

    List<SocialIdentityVO> listOwnedIdentities(Long userId);

    void unbindOwnedIdentity(Long userId, Long identityId);
}