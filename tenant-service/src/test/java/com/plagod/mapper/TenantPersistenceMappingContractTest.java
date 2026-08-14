package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantPersistenceMappingContractTest {

    @Test
    void newPersistenceMappersRemainBasicMappings() {
        for (Class<?> mapper : Arrays.asList(
                SaasPlanVersionMapper.class,
                TenantQuotaMapper.class,
                TenantUsageDailyMapper.class,
                TenantPlanAssignmentMapper.class,
                TenantQuotaReservationMapper.class,
                TenantCreationReceiptMapper.class,
                TenantDomainOutboxMapper.class)) {
            assertTrue(BaseMapper.class.isAssignableFrom(mapper));
            assertEquals(0, mapper.getDeclaredMethods().length);
        }
    }
}
