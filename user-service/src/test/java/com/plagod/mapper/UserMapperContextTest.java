package com.plagod.mapper;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.plagod.UserApplication;
import com.plagod.job.EntitlementOrderTimeoutJob;
import com.plagod.testkit.MapperContextAssertions;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;

@SpringBootTest(
        classes = UserApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.bootstrap.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "spring.task.scheduling.enabled=false",
                "wifi.audit.enabled=false",
                "wifi.security.enabled=false",
                "wifi.internal.token=user-mapper-context-test-token"
        })
class UserMapperContextTest {

    @MockBean
    private DataSource dataSource;

    @MockBean
    private EntitlementOrderTimeoutJob entitlementOrderTimeoutJob;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MybatisPlusProperties mybatisPlusProperties;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void realUserContextRegistersOnlyOwnedMappersAndLoadsXml()
            throws Exception {
        MapperContextAssertions.assertExactApplicationMapperBeans(
                applicationContext,
                sqlSessionFactory,
                DefaultTenantMembershipOutboxMapper.class,
                DurationPurchaseMapper.class,
                EntitlementOrderMapper.class,
                EntitlementUsageLogMapper.class,
                NetworkEntitlementMapper.class,
                PaymentRecordMapper.class,
                RefundRecordMapper.class,
                SocialIdentityMapper.class,
                TradeStatusLogMapper.class,
                UserAccountCommandReceiptMapper.class,
                UserMapper.class,
                UserOperationRequestMapper.class);
        MapperContextAssertions.assertPackagedMapperXmlStatementsLoaded(
                mybatisPlusProperties.resolveMapperLocations(),
                "classpath*:com/plagod/mapper/xml/*Mapper.xml",
                sqlSessionFactory,
                EntitlementOrderMapper.class.getName()
                        + ".insertOrResolveExisting",
                NetworkEntitlementMapper.class.getName()
                        + ".deductRemainingSeconds",
                PaymentRecordMapper.class.getName()
                        + ".insertOrResolveExisting",
                RefundRecordMapper.class.getName()
                        + ".insertOrResolveExisting",
                TradeStatusLogMapper.class.getName()
                        + ".insertIgnore");
    }
}
