package com.plagod.service;

import com.plagod.dto.user.SocialIdentityResolveDTO;
import com.plagod.entity.user.SocialIdentity;
import com.plagod.entity.user.User;
import com.plagod.entity.auth.DefaultTenantMembershipOutbox;
import com.plagod.mapper.DefaultTenantMembershipOutboxMapper;
import com.plagod.mapper.SocialIdentityMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.utils.PasswordUtils;
import com.plagod.vo.user.SocialIdentityResolveResultVO;
import com.plagod.vo.user.SocialIdentityVO;
import com.plagod.vo.user.SocialLoginPrincipalVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class SocialIdentityTransactionService {

    @Autowired
    private SocialIdentityMapper socialIdentityMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DefaultTenantMembershipOutboxMapper defaultTenantMembershipOutboxMapper;

    @Transactional
    public SocialIdentityResolveResultVO resolveLogin(SocialIdentityResolveDTO dto) {
        SocialIdentity existing = findExistingIdentityForUpdate(dto);

        if (existing != null) {
            User user = requireAvailableUserForUpdate(existing.getUserId());
            updateIdentityProfile(existing, dto, true);
            return SocialIdentityResolveResultVO.loginReady(toPrincipal(user), toIdentityVO(existing));
        }

        String verifiedEmail = dto.getVerifiedEmail();
        if (StringUtils.hasText(verifiedEmail)) {
            User emailOwner = userMapper.selectByEmailIncludingDeletedForUpdate(verifiedEmail);
            if (emailOwner != null) {
                return SocialIdentityResolveResultVO.bindRequired("该邮箱已关联本地账号，请先登录该账号后显式绑定");
            }
        }

        User user = createSocialUser(dto);
        userMapper.insert(user);
        enqueueDefaultTenantMembership(user);

        SocialIdentity identity = createIdentity(user.getUserId(), dto, true);
        socialIdentityMapper.insert(identity);

        return SocialIdentityResolveResultVO.loginReady(toPrincipal(user), toIdentityVO(identity));
    }

    @Transactional
    public SocialIdentityResolveResultVO resolveBind(SocialIdentityResolveDTO dto) {

        // 所有社交身份操作统一先锁身份，再锁用户，避免交叉死锁。
        SocialIdentity externalIdentity = findExistingIdentityForUpdate(dto);

        User user = requireBindUserForUpdate(dto);

        if (externalIdentity != null) {
            if (!Objects.equals(externalIdentity.getUserId(), user.getUserId())) {
                throw new IllegalArgumentException("该社交身份已绑定其他本地账号");
            }

            updateIdentityProfile(externalIdentity, dto, false);
            return SocialIdentityResolveResultVO.bindReady(toPrincipal(user), toIdentityVO(externalIdentity));
        }

        SocialIdentity providerBinding = socialIdentityMapper.selectByUserAndProviderForUpdate(user.getUserId(), dto.getProvider());

        if (providerBinding != null) {
            throw new IllegalArgumentException("当前账号已经绑定该类型的社交身份");
        }

        SocialIdentity identity = createIdentity(user.getUserId(), dto, false);

        socialIdentityMapper.insert(identity);

        return SocialIdentityResolveResultVO.bindReady(toPrincipal(user), toIdentityVO(identity));
    }

    @Transactional
    public void unbind(Long userId, Long identityId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户 ID 无效");
        }

        // 先锁身份，再锁用户，与登录、绑定和物理删除保持一致。
        SocialIdentity identity = socialIdentityMapper.selectOwnedByIdForUpdate(identityId, userId);

        if (identity == null) {
            throw new IllegalArgumentException("社交身份不存在或不属于当前用户");
        }

        User user = requireAvailableUserForUpdate(userId);

        int identityCount = socialIdentityMapper.countByUserId(userId);

        boolean hasLocalContact = StringUtils.hasText(user.getEmail()) || StringUtils.hasText(user.getPhone());

        if (!hasLocalContact && identityCount <= 1) {
            throw new IllegalArgumentException("当前账号没有邮箱、手机号或其他登录身份，不能解绑最后一个社交身份");
        }

        int deleted = socialIdentityMapper.physicalDeleteOwned(identityId, userId);

        if (deleted != 1) {
            throw new IllegalStateException("社交身份解绑失败");
        }
    }


    private SocialIdentity findExistingIdentityForUpdate(SocialIdentityResolveDTO dto) {

        SocialIdentity subjectIdentity = socialIdentityMapper.selectBySubjectForUpdate(dto.getProvider(), dto.getProviderSubject());

        SocialIdentity unionIdentity = null;
        if (StringUtils.hasText(dto.getProviderUnionId())) {
            unionIdentity = socialIdentityMapper.selectByUnionIdForUpdate(dto.getProvider(), dto.getProviderUnionId());
        }

        if (subjectIdentity != null && unionIdentity != null && !Objects.equals(subjectIdentity.getIdentityId(), unionIdentity.getIdentityId())) {
            throw new IllegalArgumentException("openid 与 unionid 指向不同账号");
        }

        SocialIdentity existing = subjectIdentity != null ? subjectIdentity : unionIdentity;


        if (existing != null && !Objects.equals(existing.getProviderSubject(), dto.getProviderSubject())) {
            throw new IllegalArgumentException("社交身份的稳定标识发生冲突");
        }

        if (existing != null && StringUtils.hasText(existing.getProviderUnionId()) && StringUtils.hasText(dto.getProviderUnionId()) && !Objects.equals(existing.getProviderUnionId(), dto.getProviderUnionId())) {
            throw new IllegalArgumentException("社交身份 unionid 发生冲突");
        }

        return existing;
    }

    private User requireBindUserForUpdate(SocialIdentityResolveDTO dto) {

        String verifiedEmail = dto.getVerifiedEmail();

        if (StringUtils.hasText(verifiedEmail)) {
            // 如果第三方 verified email 已有本地归属，只允许绑定到该账号。
            User emailOwner = userMapper.selectByEmailIncludingDeletedForUpdate(verifiedEmail);

            if (emailOwner != null) {
                if (!Objects.equals(emailOwner.getUserId(), dto.getBindUserId())) {
                    throw new IllegalArgumentException("该第三方账号的验证邮箱属于另一个本地账号");
                }

                return requireAvailableLockedUser(emailOwner);
            }
        }

        return requireAvailableUserForUpdate(dto.getBindUserId());
    }

    private User requireAvailableUserForUpdate(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户 ID 无效");
        }

        User user = userMapper.selectByIdForUpdate(userId);
        return requireAvailableLockedUser(user);
    }

    private User requireAvailableLockedUser(User user) {
        if (user == null || Integer.valueOf(1).equals(user.getDelFlag())) {
            throw new IllegalArgumentException("用户不存在或已删除");
        }

        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new IllegalArgumentException("用户当前不可用");
        }

        return user;
    }

    private User createSocialUser(SocialIdentityResolveDTO dto) {
        String randomPart = UUID.randomUUID().toString().replace("-", "");

        User user = new User();
        user.setUsername("oauth_" + dto.getProvider() + "_" + randomPart);
        user.setPassword(PasswordUtils.encode(UUID.randomUUID().toString() + UUID.randomUUID().toString()));
        user.setNickname(resolveNickname(dto));
        user.setEmail(dto.getVerifiedEmail());
        user.setAvatar(resolveLocalAvatar(dto.getAvatarUrl()));
        user.setRole(2);
        user.setStatus(1);
        user.setDelFlag(0);
        return user;
    }

    private void enqueueDefaultTenantMembership(User user) {
        DefaultTenantMembershipOutbox outbox = new DefaultTenantMembershipOutbox();
        outbox.setEventId(UUID.randomUUID().toString());
        outbox.setUserId(user.getUserId());
        outbox.setRole(user.getRole());
        outbox.setStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setNextRetryTime(LocalDateTime.now());
        defaultTenantMembershipOutboxMapper.insert(outbox);
    }

    private String resolveNickname(SocialIdentityResolveDTO dto) {
        String nickname = dto.getDisplayName();

        if (!StringUtils.hasText(nickname)) {
            nickname = dto.getProviderUsername();
        }
        if (!StringUtils.hasText(nickname)) {
            nickname = "社交用户";
        }

        return nickname.length() <= 64 ? nickname : nickname.substring(0, 64);
    }

    private String resolveLocalAvatar(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl) || avatarUrl.length() > 255) {
            return null;
        }
        return avatarUrl;
    }

    private SocialIdentity createIdentity(Long userId, SocialIdentityResolveDTO dto, boolean login) {

        LocalDateTime now = LocalDateTime.now();

        SocialIdentity identity = new SocialIdentity();
        identity.setUserId(userId);
        identity.setProvider(dto.getProvider());
        identity.setProviderSubject(dto.getProviderSubject());
        identity.setProviderUnionId(dto.getProviderUnionId());
        identity.setProviderUsername(dto.getProviderUsername());
        identity.setDisplayName(dto.getDisplayName());
        identity.setAvatarUrl(dto.getAvatarUrl());
        identity.setEmail(dto.getVerifiedEmail());
        identity.setEmailVerified(StringUtils.hasText(dto.getVerifiedEmail()) ? 1 : 0);
        identity.setBindTime(now);

        if (login) {
            identity.setLastLoginTime(now);
        }

        return identity;
    }

    private void updateIdentityProfile(SocialIdentity identity, SocialIdentityResolveDTO dto, boolean login) {

        if (!StringUtils.hasText(identity.getProviderUnionId()) && StringUtils.hasText(dto.getProviderUnionId())) {
            identity.setProviderUnionId(dto.getProviderUnionId());
        }
        if (StringUtils.hasText(dto.getProviderUsername())) {
            identity.setProviderUsername(dto.getProviderUsername());
        }
        if (StringUtils.hasText(dto.getDisplayName())) {
            identity.setDisplayName(dto.getDisplayName());
        }
        if (StringUtils.hasText(dto.getAvatarUrl())) {
            identity.setAvatarUrl(dto.getAvatarUrl());
        }
        if (StringUtils.hasText(dto.getVerifiedEmail())) {
            identity.setEmail(dto.getVerifiedEmail());
            identity.setEmailVerified(1);
        }
        if (login) {
            identity.setLastLoginTime(LocalDateTime.now());
        }

        socialIdentityMapper.updateById(identity);
    }

    private SocialLoginPrincipalVO toPrincipal(User user) {
        SocialLoginPrincipalVO principal = new SocialLoginPrincipalVO();
        principal.setUserId(user.getUserId());
        principal.setUsername(user.getUsername());
        principal.setRole(user.getRole());
        principal.setNickname(user.getNickname());
        principal.setAvatar(user.getAvatar());
        return principal;
    }

    private SocialIdentityVO toIdentityVO(SocialIdentity identity) {
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
}
