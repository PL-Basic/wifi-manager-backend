package com.plagod.service;

public interface DefaultTenantMembershipOutboxService {

    void dispatchForUser(Long userId);

    boolean isMembershipReady(Long userId);
}
