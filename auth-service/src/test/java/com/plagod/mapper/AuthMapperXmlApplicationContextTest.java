package com.plagod.mapper;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.plagod.AuthApplication;
import com.plagod.startup.AutoStartupCheck;
import com.plagod.testkit.MapperContextAssertions;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = AuthApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.bootstrap.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "spring.task.scheduling.enabled=false",
                "spring.mail.test-connection=false",
                "wifi.audit.enabled=false",
                "wifi.rate-limit.redis-enabled=false",
                "wifi.security.enabled=false",
                "wifi.internal.token=auth-mapper-context-test-token",
                "wifi.jwt.secret=auth-mapper-context-test-secret-32-bytes",
                "wifi.jwt.expiration-millis=900000",
                "wifi.jwt.issuer=wifi-manager-test",
                "wifi.jwt.algorithm=HS256"
        })
class AuthMapperXmlApplicationContextTest {

    private static final String SESSION_MAPPER =
            AuthRefreshSessionMapper.class.getName() + ".";
    private static final String TOKEN_MAPPER =
            AuthRefreshTokenMapper.class.getName() + ".";
    private static final String VERIFY_MAPPER =
            VerifyCodeMapper.class.getName() + ".";

    @MockBean
    private DataSource dataSource;

    @MockBean
    private AutoStartupCheck autoStartupCheck;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private JavaMailSender javaMailSender;

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MybatisPlusProperties mybatisPlusProperties;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private AuthRefreshSessionMapper authRefreshSessionMapper;

    @Autowired
    private AuthRefreshTokenMapper authRefreshTokenMapper;

    @Test
    void realAuthContextLoadsRefreshMapperXmlStatements() {
        MapperContextAssertions.assertExactApplicationMapperBeans(
                applicationContext,
                sqlSessionFactory,
                AuthRefreshRiskEventMapper.class,
                AuthRefreshSessionMapper.class,
                AuthRefreshTokenMapper.class,
                LoginFailRecordMapper.class,
                OAuthStateMapper.class,
                VerifyCodeMapper.class);
        assertNotNull(authRefreshSessionMapper);
        assertNotNull(authRefreshTokenMapper);
        assertEquals(
                "classpath:com/plagod/mapper/xml/AuthRefreshSessionMapper.xml",
                environment.getProperty("mybatis-plus.mapper-locations[0]"));
        assertEquals(
                "classpath:com/plagod/mapper/xml/AuthRefreshTokenMapper.xml",
                environment.getProperty("mybatis-plus.mapper-locations[1]"));
        assertEquals(
                Arrays.asList(
                        "classpath:com/plagod/mapper/xml/AuthRefreshSessionMapper.xml",
                        "classpath:com/plagod/mapper/xml/AuthRefreshTokenMapper.xml"),
                Arrays.asList(mybatisPlusProperties.getMapperLocations()));

        Resource[] mapperResources =
                mybatisPlusProperties.resolveMapperLocations();
        assertEquals(
                2,
                mapperResources.length,
                Arrays.toString(mapperResources));
        try {
            MapperContextAssertions.assertPackagedMapperXmlStatementsLoaded(
                    mapperResources,
                    "classpath*:com/plagod/mapper/xml/*Mapper.xml",
                    sqlSessionFactory);
        } catch (Exception exception) {
            throw new AssertionError(
                    "Auth Mapper XML assembly failed",
                    exception);
        }

        Arrays.asList(
                SESSION_MAPPER + "selectForUpdate",
                SESSION_MAPPER + "rotate",
                SESSION_MAPPER + "updateContext",
                SESSION_MAPPER + "revokeFamily",
                SESSION_MAPPER + "revokeAllForUser",
                SESSION_MAPPER + "markStepUpRequired",
                TOKEN_MAPPER + "selectByHash",
                TOKEN_MAPPER + "selectByHashForUpdate",
                TOKEN_MAPPER + "markRotated",
                TOKEN_MAPPER + "markReplayed",
                TOKEN_MAPPER + "revokeActiveForSession",
                TOKEN_MAPPER + "revokeActiveForUser"
        ).forEach(this::assertXmlStatement);

        assertSqlContains(
                SESSION_MAPPER + "revokeFamily",
                "and status = 'active'");
        assertSqlContains(
                SESSION_MAPPER + "revokeAllForUser",
                "and status = 'active'");
        assertSqlContains(
                TOKEN_MAPPER + "revokeActiveForSession",
                "and status = 'active'");
        assertSqlContains(
                TOKEN_MAPPER + "revokeActiveForUser",
                "and token.status = 'active'");
        assertSqlContains(
                VERIFY_MAPPER + "tryClaimVerification",
                "verify_claim_owner is null or verify_lease_until <=");
        assertSqlContains(
                VERIFY_MAPPER + "tryClaimVerification",
                "and verify_status = 0 and status = 0");
        assertSqlContains(
                VERIFY_MAPPER + "releaseVerificationClaim",
                "and verify_claim_owner =");
        assertSqlContains(
                VERIFY_MAPPER + "finalizeSendSuccess",
                "and provider_out_id <=>");
        assertSqlContains(
                VERIFY_MAPPER + "finalizeSendSuccess",
                "and send_status = 0");
        assertSqlContains(
                VERIFY_MAPPER + "finalizeSendFailure",
                "and provider_out_id <=>");
        assertSqlContains(
                VERIFY_MAPPER + "finalizeSendFailure",
                "and send_status = 0");
    }

    private void assertXmlStatement(String statementId) {
        assertTrue(
                sqlSessionFactory.getConfiguration().hasStatement(
                        statementId,
                        false),
                statementId);
        MappedStatement statement =
                sqlSessionFactory.getConfiguration()
                        .getMappedStatement(statementId, false);
        String expectedXml = statementId.startsWith(SESSION_MAPPER)
                ? "AuthRefreshSessionMapper.xml"
                : "AuthRefreshTokenMapper.xml";
        assertTrue(
                statement.getResource().contains(expectedXml),
                statementId + " loaded from " + statement.getResource());
    }

    private void assertSqlContains(
            String statementId,
            String expectedFragment) {
        String sql = sqlSessionFactory.getConfiguration()
                .getMappedStatement(statementId, false)
                .getBoundSql(Collections.emptyMap())
                .getSql()
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        assertTrue(
                sql.contains(expectedFragment),
                statementId + " SQL: " + sql);
    }
}
