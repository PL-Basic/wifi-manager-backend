package com.plagod.service.impl;

import com.plagod.constant.OAuthProvider;
import com.plagod.constant.OAuthPurpose;
import com.plagod.dto.user.SocialIdentityResolveDTO;
import com.plagod.entity.user.SocialIdentity;
import com.plagod.entity.user.User;
import com.plagod.mapper.SocialIdentityMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.service.SocialIdentityService;
import com.plagod.service.SocialIdentityTransactionService;
import com.plagod.vo.user.SocialIdentityResolveResultVO;
import com.plagod.vo.user.SocialIdentityVO;
import com.plagod.vo.user.SocialLoginPrincipalVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class SocialIdentityServiceImpl implements SocialIdentityService {

    @Autowired
    private SocialIdentityTransactionService transactionService;

    @Autowired
    private SocialIdentityMapper socialIdentityMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public SocialIdentityResolveResultVO resolve(SocialIdentityResolveDTO resolveDTO) {

        OAuthPurpose purpose = normalize(resolveDTO);

        try {
            return execute(resolveDTO, purpose);
        } catch (DuplicateKeyException | TransientDataAccessException firstException) {

            log.info("OAuth 身份解析发生并发数据库竞争，执行一次重试");

            try {
                return execute(resolveDTO, purpose);
            } catch (DuplicateKeyException | TransientDataAccessException secondException) {

                throw new IllegalArgumentException("社交身份绑定发生并发冲突，请稍后重试");
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SocialLoginPrincipalVO getLoginPrincipal(Long userId) {
        User user = requireAvailableUser(userId);

        SocialLoginPrincipalVO principal = new SocialLoginPrincipalVO();
        principal.setUserId(user.getUserId());
        principal.setUsername(user.getUsername());
        principal.setRole(user.getRole());
        principal.setNickname(user.getNickname());
        principal.setAvatar(user.getAvatar());
        return principal;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SocialIdentityVO> listOwnedIdentities(Long userId) {
        requireAvailableUser(userId);

        List<SocialIdentityVO> result = new ArrayList<>();
        for (SocialIdentity identity : socialIdentityMapper.selectByUserId(userId)) {
            result.add(toVO(identity));
        }
        return result;
    }

    @Override
    public void unbindOwnedIdentity(Long userId, Long identityId) {
        if (identityId == null || identityId <= 0) {
            throw new IllegalArgumentException("社交身份 ID 无效");
        }
        transactionService.unbind(userId, identityId);
    }

    private SocialIdentityResolveResultVO execute(SocialIdentityResolveDTO dto, OAuthPurpose purpose) {

        if (purpose == OAuthPurpose.LOGIN) {
            return transactionService.resolveLogin(dto);
        }
        return transactionService.resolveBind(dto);
    }

    private OAuthPurpose normalize(SocialIdentityResolveDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("社交身份参数不能为空");
        }

        OAuthProvider provider = OAuthProvider.parse(dto.getProvider());
        OAuthPurpose purpose = OAuthPurpose.parse(dto.getPurpose());

        dto.setProvider(provider.value());
        dto.setPurpose(purpose.value());
        dto.setProviderSubject(requireText(dto.getProviderSubject(), "providerSubject"));
        dto.setProviderUnionId(clean(dto.getProviderUnionId()));
        dto.setProviderUsername(clean(dto.getProviderUsername()));
        dto.setDisplayName(clean(dto.getDisplayName()));
        dto.setAvatarUrl(clean(dto.getAvatarUrl()));

        if (provider != OAuthProvider.WECHAT && StringUtils.hasText(dto.getProviderUnionId())) {
            throw new IllegalArgumentException("只有微信身份可以携带 providerUnionId");
        }

        if (Boolean.TRUE.equals(dto.getEmailVerified()) && StringUtils.hasText(dto.getVerifiedEmail())) {
            dto.setVerifiedEmail(dto.getVerifiedEmail().trim().toLowerCase(Locale.ROOT));
        } else {
            dto.setVerifiedEmail(null);
            dto.setEmailVerified(false);
        }

        if (purpose == OAuthPurpose.BIND) {
            if (dto.getBindUserId() == null || dto.getBindUserId() <= 0) {
                throw new IllegalArgumentException("绑定操作缺少有效的 bindUserId");
            }
        } else {
            dto.setBindUserId(null);
        }

        return purpose;
    }

    private User requireAvailableUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户 ID 无效");
        }

        User user = userMapper.selectByIdIncludingDeleted(userId);
        if (user == null || Integer.valueOf(1).equals(user.getDelFlag())) {
            throw new IllegalArgumentException("用户不存在或已删除");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new IllegalArgumentException("用户当前不可用");
        }
        return user;
    }

    private SocialIdentityVO toVO(SocialIdentity identity) {
        SocialIdentityVO vo = new SocialIdentityVO();
        vo.setIdentityId(identity.getIdentityId());
        vo.setUserId(identity.getUserId());
        vo.setProvider(identity.getProvider());
        vo.setProviderUsername(identity.getProviderUsername());
        vo.setDisplayName(identity.getDisplayName());
        vo.setAvatarUrl(identity.getAvatarUrl());
        vo.setEmail(identity.getEmail());
        vo.setEmailVerified(Integer.valueOf(1).equals(identity.getEmailVerified()));
        vo.setBindTime(identity.getBindTime());
        vo.setLastLoginTime(identity.getLastLoginTime());
        return vo;
    }

    private String requireText(String value, String fieldName) {
        String cleaned = clean(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return cleaned;
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}