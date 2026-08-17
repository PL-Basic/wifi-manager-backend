package com.plagod.service.impl;

import com.plagod.dto.user.UserStatusDTO;
import com.plagod.entity.user.User;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.SocialIdentityMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.service.UserAuthSessionRevokeOutboxAppender;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserManageServiceImplTest {

    @Test
    void purgeRejectsUserWithEntitlementHistoryBeforeExternalSideEffects() {
        UserMapper userMapper = mock(UserMapper.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SocialIdentityMapper socialIdentityMapper = mock(SocialIdentityMapper.class);
        UserAuthSessionRevokeOutboxAppender outboxAppender =
                mock(UserAuthSessionRevokeOutboxAppender.class);

        User user = new User();
        user.setUserId(7L);
        user.setRole(2);

        when(userMapper.selectByIdForUpdate(7L)).thenReturn(user);
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                eq(7L),
                eq(7L),
                eq(7L),
                eq(7L),
                eq(7L),
                eq(7L)
        )).thenReturn(1);

        UserManageServiceImpl service = new UserManageServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "socialIdentityMapper", socialIdentityMapper);
        ReflectionTestUtils.setField(
                service,
                "revokeOutboxAppender",
                outboxAppender);

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.purgeUser(7L)
        );

        assertEquals(409, exception.getHttpStatus());
        verifyNoInteractions(outboxAppender, socialIdentityMapper);
    }

    @Test
    void disablingUserAppendsAuthRevokeEventInsteadOfCallingAuth() {
        UserMapper userMapper = mock(UserMapper.class);
        UserAuthSessionRevokeOutboxAppender outboxAppender =
                mock(UserAuthSessionRevokeOutboxAppender.class);
        User user = new User();
        user.setUserId(7L);
        user.setRole(2);
        user.setStatus(1);
        UserStatusDTO request = new UserStatusDTO();
        request.setStatus(0);

        when(userMapper.selectById(7L)).thenReturn(user);
        when(userMapper.updateById(user)).thenReturn(1);

        UserManageServiceImpl service = new UserManageServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(
                service,
                "revokeOutboxAppender",
                outboxAppender);

        service.updateStatus(7L, request);

        verify(outboxAppender).append(7L, "ACCOUNT_DISABLED");
        verify(userMapper).updateById(user);
    }

    @Test
    void accountMutationsKeepBusinessStateAndOutboxInLocalTransactions()
            throws Exception {
        assertTransactional(
                "updateUser",
                Long.class,
                com.plagod.dto.user.UserUpdateDTO.class,
                Integer.class);
        assertTransactional(
                "updateStatus",
                Long.class,
                UserStatusDTO.class);
        assertTransactional("deleteUser", Long.class);
        assertTransactional("purgeUser", Long.class);
    }

    private void assertTransactional(
            String methodName,
            Class<?>... parameterTypes) throws Exception {
        Method method = UserManageServiceImpl.class.getMethod(
                methodName,
                parameterTypes);
        assertNotNull(method.getAnnotation(Transactional.class));
    }
}
