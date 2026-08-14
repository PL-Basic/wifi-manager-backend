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
    void allMappersRemainBasicAndTaskKeepsLeaseColumns() throws Exception {
        for (Class<?> mapper : Arrays.asList(
                AiProviderMapper.class,
                AiPolicyMapper.class,
                AiPolicyVersionMapper.class,
                AiReviewTaskMapper.class,
                AiManualReviewMapper.class)) {
            assertTrue(BaseMapper.class.isAssignableFrom(mapper));
            assertEquals(0, mapper.getDeclaredMethods().length);
        }
        assertNotNull(AiReviewTask.class.getDeclaredField("workerId"));
        assertNotNull(AiReviewTask.class.getDeclaredField("leaseUntil"));
        assertNotNull(AiReviewTask.class.getDeclaredField("claimedTime"));
    }
}
