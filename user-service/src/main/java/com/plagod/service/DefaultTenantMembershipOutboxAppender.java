package com.plagod.service;

public interface DefaultTenantMembershipOutboxAppender {

    void append(
            Long userId,
            Integer role,
            String idempotencyKey,
            String requestFingerprint);
}
