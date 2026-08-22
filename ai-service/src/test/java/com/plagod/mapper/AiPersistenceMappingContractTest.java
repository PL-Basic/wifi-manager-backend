package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.AiReviewTask;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPersistenceMappingContractTest {

    @Test
    void nonTaskMappersRemainBasicAndTaskKeepsLeaseColumns() throws Exception {
        for (Class<?> mapper : Arrays.asList(
                AiProviderMapper.class,
                AiPolicyMapper.class,
                AiManualReviewMapper.class)) {
            assertTrue(BaseMapper.class.isAssignableFrom(mapper));
            assertEquals(0, mapper.getDeclaredMethods().length);
        }
        assertTrue(BaseMapper.class.isAssignableFrom(AiReviewTaskMapper.class));
        assertTrue(BaseMapper.class.isAssignableFrom(
                AiPolicyVersionMapper.class));
        assertNotNull(AiPolicyVersionMapper.class.getMethod(
                "selectActiveByScene",
                String.class));
        assertNotNull(AiReviewTask.class.getDeclaredField("workerId"));
        assertNotNull(AiReviewTask.class.getDeclaredField("leaseUntil"));
        assertNotNull(AiReviewTask.class.getDeclaredField("claimedTime"));
    }
}
