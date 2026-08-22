package com.plagod.service.impl;

import com.plagod.entity.auth.DefaultTenantMembershipOutbox;
import com.plagod.entity.auth.UserAuthSessionRevokeOutbox;
import com.plagod.mapper.DefaultTenantMembershipOutboxMapper;
import com.plagod.mapper.UserAuthSessionRevokeOutboxMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultTenantMembershipOutboxAppenderImplTest {

    @Test
    void appendsPendingEventThroughUserOwnedMapper() {
        DefaultTenantMembershipOutboxMapper mapper =
                mock(DefaultTenantMembershipOutboxMapper.class);
        when(mapper.insert(org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        DefaultTenantMembershipOutboxAppenderImpl appender =
                new DefaultTenantMembershipOutboxAppenderImpl(mapper);

        appender.append(7L, 2, "register-7", "fingerprint-7");

        ArgumentCaptor<DefaultTenantMembershipOutbox> captor =
                ArgumentCaptor.forClass(
                        DefaultTenantMembershipOutbox.class);
        verify(mapper).insert(captor.capture());
        DefaultTenantMembershipOutbox outbox = captor.getValue();
        assertNotNull(outbox.getEventId());
        assertEquals("register-7", outbox.getIdempotencyKey());
        assertEquals("fingerprint-7", outbox.getRequestFingerprint());
        assertEquals(7L, outbox.getUserId());
        assertEquals(2, outbox.getRole());
        assertEquals("PENDING", outbox.getStatus());
        assertEquals(0, outbox.getRetryCount());
        assertNotNull(outbox.getNextRetryTime());
    }

    @Test
    void appendsPendingAuthRevokeEventWithoutChangingAuthContract() {
        UserAuthSessionRevokeOutboxMapper mapper =
                mock(UserAuthSessionRevokeOutboxMapper.class);
        when(mapper.insert(org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        UserAuthSessionRevokeOutboxAppenderImpl appender =
                new UserAuthSessionRevokeOutboxAppenderImpl(mapper);

        appender.append(7L, "ACCOUNT_DISABLED");

        ArgumentCaptor<UserAuthSessionRevokeOutbox> captor =
                ArgumentCaptor.forClass(
                        UserAuthSessionRevokeOutbox.class);
        verify(mapper).insert(captor.capture());
        UserAuthSessionRevokeOutbox outbox = captor.getValue();
        assertNotNull(outbox.getEventId());
        assertEquals(7L, outbox.getUserId());
        assertEquals("ACCOUNT_DISABLED", outbox.getRevokeReason());
        assertEquals("PENDING", outbox.getStatus());
        assertEquals(0, outbox.getRetryCount());
        assertNotNull(outbox.getNextRetryTime());
    }
}
