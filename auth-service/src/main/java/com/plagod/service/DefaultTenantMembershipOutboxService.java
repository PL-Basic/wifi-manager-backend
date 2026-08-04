package com.plagod.service;

public interface DefaultTenantMembershipOutboxService {
    void enqueue(Long userId, Integer role);

    void dispatch(String eventId);

    void dispatchForUser(Long userId);

    void retryDueEvents();

    boolean isMembershipReady(Long userId);
}
