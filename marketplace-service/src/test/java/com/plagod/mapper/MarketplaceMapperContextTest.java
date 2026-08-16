package com.plagod.mapper;

import com.plagod.testkit.MapperContextAssertions;
import org.junit.jupiter.api.Test;

class MarketplaceMapperContextTest {

    @Test
    void registersOnlyMarketplaceMappers() {
        MapperContextAssertions.assertExactMapperBeans(
                "com.plagod.mapper",
                MarketplaceFulfillmentMapper.class,
                MarketplaceOrderItemMapper.class,
                MarketplaceOrderMapper.class,
                MarketplaceProductMapper.class,
                MarketplaceSkuMapper.class);
    }
}
