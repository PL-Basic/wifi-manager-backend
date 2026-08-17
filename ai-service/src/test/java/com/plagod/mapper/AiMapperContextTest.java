package com.plagod.mapper;

import com.plagod.testkit.MapperContextAssertions;
import org.junit.jupiter.api.Test;

class AiMapperContextTest {

    @Test
    void registersOnlyAiMappers() {
        MapperContextAssertions.assertExactMapperBeans(
                "com.plagod.mapper",
                AiManualReviewMapper.class,
                AiPolicyMapper.class,
                AiPolicyVersionMapper.class,
                AiProviderMapper.class,
                AiReviewTaskInputMapper.class,
                AiReviewTaskMapper.class);
    }
}
