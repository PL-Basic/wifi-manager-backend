package com.plagod.service.impl;

import com.plagod.entity.auth.UserAuthSessionRevokeOutbox;
import com.plagod.mapper.UserAuthSessionRevokeOutboxMapper;
import com.plagod.service.UserAuthSessionRevokeOutboxAppender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UserAuthSessionRevokeOutboxAppenderImpl
        implements UserAuthSessionRevokeOutboxAppender {

    private final UserAuthSessionRevokeOutboxMapper outboxMapper;

    public UserAuthSessionRevokeOutboxAppenderImpl(
            UserAuthSessionRevokeOutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    @Override
    public void append(Long userId, String revokeReason) {
        UserAuthSessionRevokeOutbox outbox =
                new UserAuthSessionRevokeOutbox();
        outbox.setEventId(UUID.randomUUID().toString());
        outbox.setUserId(userId);
        outbox.setRevokeReason(revokeReason);
        outbox.setStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setNextRetryTime(LocalDateTime.now());
        if (outboxMapper.insert(outbox) != 1) {
            throw new IllegalStateException("认证会话撤销事件写入失败");
        }
    }
}
