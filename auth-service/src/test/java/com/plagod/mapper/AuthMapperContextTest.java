package com.plagod.mapper;

import com.plagod.testkit.MapperContextAssertions;
import org.junit.jupiter.api.Test;

class AuthMapperContextTest {

    @Test
    void registersOnlyAuthMappers() {
        MapperContextAssertions.assertExactMapperBeans(
                "com.plagod.mapper",
                AuthRefreshRiskEventMapper.class,
                AuthRefreshSessionMapper.class,
                AuthRefreshTokenMapper.class,
                LoginFailRecordMapper.class,
                OAuthStateMapper.class,
                VerifyCodeMapper.class);
    }
}
