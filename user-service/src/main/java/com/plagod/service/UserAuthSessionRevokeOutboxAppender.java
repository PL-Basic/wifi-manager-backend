package com.plagod.service;

public interface UserAuthSessionRevokeOutboxAppender {

    void append(Long userId, String revokeReason);
}
