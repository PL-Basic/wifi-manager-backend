package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplacePersistenceMappingContractTest {

    @Test
    void allMappersRemainBasicMappings() {
        for (Class<?> mapper : Arrays.asList(
                MarketplaceProductMapper.class,
                MarketplaceSkuMapper.class,
                MarketplaceOrderMapper.class,
                MarketplaceOrderItemMapper.class,
                MarketplaceFulfillmentMapper.class)) {
            assertTrue(BaseMapper.class.isAssignableFrom(mapper));
            assertEquals(0, mapper.getDeclaredMethods().length);
        }
    }
}
