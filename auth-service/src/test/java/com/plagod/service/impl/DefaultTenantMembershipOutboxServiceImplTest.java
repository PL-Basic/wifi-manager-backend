package com.plagod.service.impl;

import com.plagod.client.TenantMembershipClient;
import com.plagod.entity.auth.DefaultTenantMembershipOutbox;
import com.plagod.mapper.DefaultTenantMembershipOutboxMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultTenantMembershipOutboxServiceImplTest {

    @Test
    void pendingOutboxKeepsNewUserRestricted() {
        DefaultTenantMembershipOutboxMapper mapper = mock(DefaultTenantMembershipOutboxMapper.class);
        DefaultTenantMembershipOutbox pending = new DefaultTenantMembershipOutbox();
        pending.setStatus("PENDING");
        when(mapper.selectOne(any())).thenReturn(pending);

        DefaultTenantMembershipOutboxServiceImpl service = service(mapper);

        assertFalse(service.isMembershipReady(7L));
    }

    @Test
    void succeededOrMigratedUserIsReady() {
        DefaultTenantMembershipOutboxMapper mapper = mock(DefaultTenantMembershipOutboxMapper.class);
        DefaultTenantMembershipOutbox succeeded = new DefaultTenantMembershipOutbox();
        succeeded.setStatus("SUCCEEDED");
        when(mapper.selectOne(any())).thenReturn(succeeded).thenReturn(null);

        DefaultTenantMembershipOutboxServiceImpl service = service(mapper);

        assertTrue(service.isMembershipReady(7L));
        assertTrue(service.isMembershipReady(8L));
    }

    private DefaultTenantMembershipOutboxServiceImpl service(DefaultTenantMembershipOutboxMapper mapper) {
        return new DefaultTenantMembershipOutboxServiceImpl(
                mapper,
                mock(TenantMembershipClient.class),
                mock(ApplicationEventPublisher.class));
    }
}
