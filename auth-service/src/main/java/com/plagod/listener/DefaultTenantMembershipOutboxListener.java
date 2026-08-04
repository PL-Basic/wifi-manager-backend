package com.plagod.listener;

import com.plagod.event.DefaultTenantMembershipOutboxCreatedEvent;
import com.plagod.service.DefaultTenantMembershipOutboxService;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DefaultTenantMembershipOutboxListener {

    private final DefaultTenantMembershipOutboxService outboxService;

    public DefaultTenantMembershipOutboxListener(DefaultTenantMembershipOutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Async("tenantMembershipOutboxExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(DefaultTenantMembershipOutboxCreatedEvent event) {
        outboxService.dispatch(event.getEventId());
    }
}
