package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportPersistenceMappingContractTest {

    @Test
    void allMappersRemainBasicMappings() {
        for (Class<?> mapper : Arrays.asList(
                AnnouncementMapper.class,
                AnnouncementContentVersionMapper.class,
                AnnouncementCommentMapper.class,
                AnnouncementActionRequestMapper.class,
                UserCapabilityRestrictionMapper.class,
                SupportUserGuardMapper.class,
                SupportDailyGuardMapper.class,
                SupportTicketMapper.class,
                SupportTicketMessageMapper.class,
                SupportTicketTransitionMapper.class)) {
            assertTrue(BaseMapper.class.isAssignableFrom(mapper));
            assertEquals(0, mapper.getDeclaredMethods().length);
        }

        assertTrue(BaseMapper.class.isAssignableFrom(
                SupportContentReviewOutboxMapper.class));
        assertTrue(BaseMapper.class.isAssignableFrom(
                SupportSubmissionMapper.class));
    }
}
