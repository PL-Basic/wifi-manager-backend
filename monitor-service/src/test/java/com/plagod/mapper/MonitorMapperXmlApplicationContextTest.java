package com.plagod.mapper;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.plagod.MonitorApplication;
import com.plagod.service.AccessRuleCache;
import com.plagod.testkit.MapperContextAssertions;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.Resource;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = MonitorApplication.class,
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
                "wifi.security.gateway-token=monitor-mapper-test-gateway-token",
                "wifi.internal.token=monitor-mapper-test-internal-token"
        })
class MonitorMapperXmlApplicationContextTest {

    private static final String AUDIT_NAMESPACE =
            AuditLogMapper.class.getName() + ".";

    private static final String OWNED_MAPPER_XML_PATTERN =
            "classpath*:com/plagod/mapper/xml/*Mapper.xml";

    @MockBean
    private DataSource dataSource;

    @MockBean
    private AccessRuleCache accessRuleCache;

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MybatisPlusProperties mybatisPlusProperties;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void realMonitorContextLoadsOwnedMapperXmlStatements() throws Exception {
        MapperContextAssertions.assertExactApplicationMapperBeans(
                applicationContext,
                sqlSessionFactory,
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
        assertNotNull(auditLogMapper);

        Resource[] configuredResources =
                mybatisPlusProperties.resolveMapperLocations();
        Set<String> configuredResourceNames = Arrays.stream(configuredResources)
                .map(Resource::getFilename)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Resource[] packagedResources =
                new PathMatchingResourcePatternResolver()
                        .getResources(OWNED_MAPPER_XML_PATTERN);
        Set<String> packagedResourceNames = Arrays.stream(packagedResources)
                .map(Resource::getFilename)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(packagedResourceNames, configuredResourceNames);
        MapperContextAssertions.assertPackagedMapperXmlStatementsLoaded(
                configuredResources,
                OWNED_MAPPER_XML_PATTERN,
                sqlSessionFactory,
                AlertEventMapper.class.getName()
                        + ".selectByIdAndTenant",
                AlertEventMapper.class.getName()
                        + ".selectByIdAndTenantForUpdate",
                AlertEventMapper.class.getName()
                        + ".handleByTenantAndVersion",
                AlertEventMapper.class.getName()
                        + ".selectAnalyticsSummary",
                AlertEventMapper.class.getName()
                        + ".selectLevelDistribution",
                AlertEventMapper.class.getName()
                        + ".selectStatusDistribution",
                AlertEventMapper.class.getName()
                        + ".selectAnalyticsSummaryByTenant",
                AlertEventMapper.class.getName()
                        + ".selectLevelDistributionByTenant",
                AlertEventMapper.class.getName()
                        + ".selectStatusDistributionByTenant",
                AuditLogMapper.class.getName()
                        + ".selectAuditPage",
                AuditLogMapper.class.getName()
                        + ".selectAuditById",
                ClientLocationMapper.class.getName()
                        + ".selectTrustedPointsForGis",
                ClientLocationMapper.class.getName()
                        + ".selectTrustedPointsForGisByTenant",
                GeofenceEventMapper.class.getName()
                        + ".selectByIdAndTenant",
                GeofenceEventMapper.class.getName()
                        + ".selectEventPage",
                GeofenceEventMapper.class.getName()
                        + ".selectEventPageByTenant",
                RuleHitRecordMapper.class.getName()
                        + ".insertIgnore",
                RuleHitRecordMapper.class.getName()
                        + ".insertIgnoreByTenant",
                RuleHitRecordMapper.class.getName()
                        + ".selectByIdAndTenant",
                RuleHitRecordMapper.class.getName()
                        + ".selectAnalyticsSummary",
                RuleHitRecordMapper.class.getName()
                        + ".selectRuleRanking",
                RuleHitRecordMapper.class.getName()
                        + ".selectActionDistribution",
                RuleHitRecordMapper.class.getName()
                        + ".selectAnalyticsSummaryByTenant",
                RuleHitRecordMapper.class.getName()
                        + ".selectRuleRankingByTenant",
                RuleHitRecordMapper.class.getName()
                        + ".selectActionDistributionByTenant");

        assertAuditSelectStatement("selectAuditPage");
        assertAuditSelectStatement("selectAuditById");
    }

    private void assertAuditSelectStatement(String methodName) {
        String statementId = AUDIT_NAMESPACE + methodName;
        assertTrue(
                sqlSessionFactory.getConfiguration().hasStatement(
                        statementId,
                        false),
                statementId);
        MappedStatement statement =
                sqlSessionFactory.getConfiguration()
                        .getMappedStatement(statementId, false);
        assertEquals(SqlCommandType.SELECT, statement.getSqlCommandType());
        assertTrue(
                statement.getResource().contains("AuditLogMapper.xml"),
                statementId + " loaded from " + statement.getResource());
    }
}
