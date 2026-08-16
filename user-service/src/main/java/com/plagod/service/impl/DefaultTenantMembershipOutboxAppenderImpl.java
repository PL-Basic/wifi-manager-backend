package com.plagod.service.impl;

import com.plagod.entity.auth.DefaultTenantMembershipOutbox;
import com.plagod.mapper.DefaultTenantMembershipOutboxMapper;
import com.plagod.service.DefaultTenantMembershipOutboxAppender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class DefaultTenantMembershipOutboxAppenderImpl
        implements DefaultTenantMembershipOutboxAppender {

    private final DefaultTenantMembershipOutboxMapper outboxMapper;

    public DefaultTenantMembershipOutboxAppenderImpl(
            DefaultTenantMembershipOutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    @Override
    public void append(
            Long userId,
            Integer role,
            String idempotencyKey,
            String requestFingerprint) {
        DefaultTenantMembershipOutbox outbox =
                new DefaultTenantMembershipOutbox();
        outbox.setEventId(UUID.randomUUID().toString());
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setRequestFingerprint(requestFingerprint);
        outbox.setUserId(userId);
        outbox.setRole(role);
        outbox.setStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setNextRetryTime(LocalDateTime.now());
        outboxMapper.insert(outbox);
    }
}
