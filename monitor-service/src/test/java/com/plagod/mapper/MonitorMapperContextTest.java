package com.plagod.mapper;

import com.plagod.testkit.MapperContextAssertions;
import org.junit.jupiter.api.Test;

class MonitorMapperContextTest {

    @Test
    void registersOnlyMonitorMappers() {
        MapperContextAssertions.assertExactMapperBeans(
                "com.plagod.mapper",
                AccessRuleMapper.class,
                AlertEventMapper.class,
                AuditLogMapper.class,
                ClientLocationMapper.class,
                GeofenceEventMapper.class,
                GeofenceMapper.class,
                GeofenceStateMapper.class,
                LocationAuthorizationMapper.class,
                MonitorHealthMapper.class,
                RuleHitRecordMapper.class);
    }
}
