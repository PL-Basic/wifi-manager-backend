package com.plagod.service;

public interface DefaultTenantMembershipOutboxService {

    void dispatchForUser(Long userId);

    void dispatchPending(int batchSize);

    boolean isMembershipReady(Long userId);
}
