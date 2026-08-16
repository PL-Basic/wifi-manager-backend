package com.plagod.mapper;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.plagod.TenantApplication;
import com.plagod.testkit.MapperContextAssertions;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

@SpringBootTest(
        classes = TenantApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.bootstrap.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "wifi.audit.enabled=false",
                "wifi.security.enabled=false",
                "wifi.internal.token=tenant-mapper-context-test-token"
        })
class TenantMapperContextTest {

    @MockBean
    private DataSource dataSource;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private MybatisPlusProperties mybatisPlusProperties;

    @Test
    void realTenantContextRegistersOnlyOwnedMappers() throws Exception {
        MapperContextAssertions.assertExactApplicationMapperBeans(
                applicationContext,
                sqlSessionFactory,
                PlatformStaffMapper.class,
                SaasPlanMapper.class,
                SaasPlanVersionMapper.class,
                TenantCreationReceiptMapper.class,
                TenantDomainOutboxMapper.class,
                TenantMapper.class,
                TenantMemberMapper.class,
                TenantPlanAssignmentMapper.class,
                TenantQuotaMapper.class,
                TenantQuotaReservationMapper.class,
                TenantSubscriptionMapper.class,
                TenantUsageDailyMapper.class);
        org.junit.jupiter.api.Assertions.assertEquals(
                0,
                mybatisPlusProperties.resolveMapperLocations().length);
        org.junit.jupiter.api.Assertions.assertEquals(
                0,
                new PathMatchingResourcePatternResolver()
                        .getResources(
                                "classpath*:com/plagod/mapper/xml/*Mapper.xml")
                        .length);
    }
}
