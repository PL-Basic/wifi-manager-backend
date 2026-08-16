package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.user.UserOperationRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserOperationMappingContractTest {

    @Test
    void keepsIdempotencyFieldsAndDefersConditionalReview() throws Exception {
        assertTrue(BaseMapper.class.isAssignableFrom(
                UserOperationRequestMapper.class));
        assertEquals(0,
                UserOperationRequestMapper.class.getDeclaredMethods().length);
        assertNotNull(UserOperationRequest.class
                .getDeclaredField("clientRequestId"));
        assertNotNull(UserOperationRequest.class
                .getDeclaredField("requestFingerprint"));
        assertNotNull(UserOperationRequest.class.getDeclaredField("version"));
    }
}
