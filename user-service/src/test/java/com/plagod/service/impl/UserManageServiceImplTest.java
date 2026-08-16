package com.plagod.service.impl;

import com.plagod.client.AuthSessionClient;
import com.plagod.entity.user.User;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.SocialIdentityMapper;
import com.plagod.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserManageServiceImplTest {

    @Test
    void purgeRejectsUserWithEntitlementHistoryBeforeExternalSideEffects() {
        UserMapper userMapper = mock(UserMapper.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SocialIdentityMapper socialIdentityMapper = mock(SocialIdentityMapper.class);
        AuthSessionClient authSessionClient = mock(AuthSessionClient.class);

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
        ReflectionTestUtils.setField(service, "authSessionClient", authSessionClient);

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.purgeUser(7L)
        );

        assertEquals(409, exception.getHttpStatus());
        verifyNoInteractions(authSessionClient, socialIdentityMapper);
    }
}
