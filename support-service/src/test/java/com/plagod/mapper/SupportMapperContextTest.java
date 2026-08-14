package com.plagod.mapper;

import com.plagod.testkit.MapperContextAssertions;
import org.junit.jupiter.api.Test;

class SupportMapperContextTest {

    @Test
    void registersOnlySupportMappers() {
        MapperContextAssertions.assertExactMapperBeans(
                "com.plagod.mapper",
                AnnouncementActionRequestMapper.class,
                AnnouncementCommentMapper.class,
                AnnouncementContentVersionMapper.class,
                AnnouncementMapper.class,
                SupportContentReviewOutboxMapper.class,
                SupportDailyGuardMapper.class,
                SupportSubmissionMapper.class,
                SupportTicketMapper.class,
                SupportTicketMessageMapper.class,
                SupportTicketTransitionMapper.class,
                SupportUserGuardMapper.class,
                UserCapabilityRestrictionMapper.class);
    }
}
