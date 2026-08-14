package com.plagod.service;

import com.plagod.dto.user.SocialIdentityResolveDTO;
import com.plagod.entity.user.SocialIdentity;
import com.plagod.entity.user.User;
import com.plagod.mapper.SocialIdentityMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.vo.user.SocialIdentityResolveResultVO;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SocialIdentityTransactionServiceOutboxTest {

    @Test
    void newOAuthUserUsesTheSingleUserOwnedOutboxAppender() {
        SocialIdentityMapper identityMapper =
                mock(SocialIdentityMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        DefaultTenantMembershipOutboxAppender outboxAppender =
                mock(DefaultTenantMembershipOutboxAppender.class);
        SocialIdentityTransactionService service =
                new SocialIdentityTransactionService();
        ReflectionTestUtils.setField(
                service,
                "socialIdentityMapper",
                identityMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(
                service,
                "defaultTenantMembershipOutboxAppender",
                outboxAppender);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(7L);
            return 1;
        });

        SocialIdentityResolveDTO request =
                new SocialIdentityResolveDTO();
        request.setProvider("github");
        request.setProviderSubject("subject-7");
        request.setProviderUsername("alice");
        request.setDisplayName("Alice");

        SocialIdentityResolveResultVO result =
                service.resolveLogin(request);

        InOrder writes = inOrder(
                userMapper,
                outboxAppender,
                identityMapper);
        writes.verify(userMapper).insert(any(User.class));
        writes.verify(outboxAppender).append(7L, 2, null, null);
        writes.verify(identityMapper).insert(any(SocialIdentity.class));
        assertEquals(7L, result.getPrincipal().getUserId());
    }
}
