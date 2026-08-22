package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplacePersistenceMappingContractTest {

    @Test
    void allMappersRemainMarketplaceOwnedMappings() {
        for (Class<?> mapper : Arrays.asList(
                MarketplaceProductMapper.class,
                MarketplaceSkuMapper.class,
                MarketplaceOrderMapper.class,
                MarketplaceOrderItemMapper.class,
                MarketplaceFulfillmentMapper.class)) {
            assertTrue(BaseMapper.class.isAssignableFrom(mapper));
        }

        assertEquals(0, MarketplaceProductMapper.class.getDeclaredMethods().length);
        assertEquals(0, MarketplaceSkuMapper.class.getDeclaredMethods().length);
        assertEquals(0, MarketplaceOrderItemMapper.class.getDeclaredMethods().length);
        assertEquals(1, MarketplaceOrderMapper.class.getDeclaredMethods().length);
        assertEquals(5, MarketplaceFulfillmentMapper.class.getDeclaredMethods().length);
    }
}
