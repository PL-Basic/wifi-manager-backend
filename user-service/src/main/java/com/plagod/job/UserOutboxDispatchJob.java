package com.plagod.job;

import com.plagod.service.DefaultTenantMembershipOutboxService;
import com.plagod.service.UserAuthSessionRevokeOutboxService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UserOutboxDispatchJob {

    private static final int BATCH_SIZE = 20;

    private final DefaultTenantMembershipOutboxService membershipOutboxService;
    private final UserAuthSessionRevokeOutboxService revokeOutboxService;

    public UserOutboxDispatchJob(
            DefaultTenantMembershipOutboxService membershipOutboxService,
            UserAuthSessionRevokeOutboxService revokeOutboxService) {
        this.membershipOutboxService = membershipOutboxService;
        this.revokeOutboxService = revokeOutboxService;
    }

    @Scheduled(
            fixedDelayString =
                    "${wifi.user-outbox.dispatch-delay-ms:1000}",
            initialDelayString =
                    "${wifi.user-outbox.initial-delay-ms:1000}")
    public void dispatch() {
        membershipOutboxService.dispatchPending(BATCH_SIZE);
        revokeOutboxService.dispatchPending(BATCH_SIZE);
    }
}
