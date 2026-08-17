package com.plagod.configuration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.client.TenantMembershipClient;
import com.plagod.dto.ApiResponse;
import com.plagod.entity.auth.DefaultTenantMembershipOutbox;
import com.plagod.entity.user.User;
import com.plagod.exception.ApiErrorKey;
import com.plagod.mapper.DefaultTenantMembershipOutboxMapper;
import com.plagod.mapper.SocialIdentityMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.service.impl.DefaultTenantMembershipOutboxServiceImpl;
import com.plagod.service.impl.UserManageServiceImpl;
import com.plagod.support.NoOpTransactionManager;
import com.plagod.support.StableUnits;
import com.plagod.web.ApiErrorResponseFactory;
import com.plagod.web.LowCardinalityTagPolicy;
import com.plagod.web.RequestIdContext;
import com.plagod.web.RequestIdFilter;
import com.plagod.web.ServletWebSupportAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class P14UserStableSupportTest {

    private static final String REQUEST_ID = "request-id-00000001";
    private static final String CANARY_SECRET =
            "P14_USER_CANARY_SECRET_8f31";

    @Test
    void sharedAdviceKeepsEnvelopeRequestIdAndSafeUnknownError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                new ApiErrorResponseFactory());
        MDC.put(RequestIdContext.MDC_KEY, REQUEST_ID);
        try {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleUnexpectedException(
                            new IllegalStateException(CANARY_SECRET));

            assertEquals(500, response.getStatusCodeValue());
            assertNotNull(response.getBody());
            assertEquals(
                    ApiErrorKey.INTERNAL_ERROR.value(),
                    response.getBody().getErrorKey());
            assertEquals(REQUEST_ID, response.getBody().getRequestId());
            assertEquals("请求处理失败", response.getBody().getMessage());
            assertFalse(response.getBody().toString()
                    .contains(CANARY_SECRET));
        } finally {
            MDC.remove(RequestIdContext.MDC_KEY);
        }
    }

    @Test
    void paymentConfigurationFailsFastWithoutEchoingSecrets() {
        PaymentProperties valid = new PaymentProperties();
        valid.setLocalDemoEnabled(true);
        valid.setLocalDemoSecret(CANARY_SECRET);
        valid.afterPropertiesSet();

        assertEquals(
                5 * StableUnits.SECONDS_PER_MINUTE,
                valid.effectiveCallbackWindowSeconds());
        assertFalse(valid.toString().contains(CANARY_SECRET));
        assertTrue(valid.toString().contains("[REDACTED]"));
        assertEquals(
                "Asia/Shanghai",
                StableUnits.ASIA_SHANGHAI.getId());

        PaymentProperties example = new PaymentProperties();
        example.setLocalDemoEnabled(true);
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                example::afterPropertiesSet);
        assertFalse(exception.getMessage()
                .contains(example.getLocalDemoSecret()));

        PaymentProperties invalidWindow = new PaymentProperties();
        invalidWindow.setCallbackWindowSeconds(0);
        assertThrows(
                IllegalStateException.class,
                invalidWindow::afterPropertiesSet);

        EntitlementProductProperties invalidCatalog =
                new EntitlementProductProperties();
        invalidCatalog.setOrderExpireMinutes(0);
        assertThrows(
                IllegalStateException.class,
                invalidCatalog::afterPropertiesSet);
    }

    @Test
    void paymentConfigurationRejectsLocalDemoInProdOnly() {
        PaymentProperties prod = new PaymentProperties();
        MockEnvironment prodEnvironment = new MockEnvironment();
        prodEnvironment.setActiveProfiles("prod");
        prod.setEnvironment(prodEnvironment);
        prod.setLocalDemoEnabled(true);
        prod.setLocalDemoSecret(CANARY_SECRET);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                prod::afterPropertiesSet);
        assertFalse(exception.getMessage().contains(CANARY_SECRET));
        assertFalse(prod.toString().contains(CANARY_SECRET));
        assertFalse(prod.toString().contains("MockEnvironment"));

        PaymentProperties nonProd = new PaymentProperties();
        MockEnvironment nonProdEnvironment = new MockEnvironment();
        nonProdEnvironment.setActiveProfiles("dev");
        nonProd.setEnvironment(nonProdEnvironment);
        nonProd.setLocalDemoEnabled(true);
        nonProd.setLocalDemoSecret(CANARY_SECRET);
        nonProd.afterPropertiesSet();

        assertTrue(nonProd.isLocalDemoEnabled());
        assertFalse(nonProd.toString().contains(CANARY_SECRET));
        assertFalse(nonProd.toString().contains("MockEnvironment"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void userPagingUsesSharedBoundsAndStableSecondarySort() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserManageServiceImpl service = new UserManageServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(
                service,
                "jdbcTemplate",
                mock(JdbcTemplate.class));
        ReflectionTestUtils.setField(
                service,
                "socialIdentityMapper",
                mock(SocialIdentityMapper.class));
        service.pageUsers(-9, 1000, null);

        ArgumentCaptor<Page> pageCaptor =
                ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<Wrapper> wrapperCaptor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).selectPage(
                pageCaptor.capture(),
                wrapperCaptor.capture());

        assertEquals(1L, pageCaptor.getValue().getCurrent());
        assertEquals(100L, pageCaptor.getValue().getSize());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("create_time"));
        assertTrue(sql.contains("user_id"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void outboxFailureDoesNotPersistOrLogExceptionMessage() {
        DefaultTenantMembershipOutboxMapper outboxMapper =
                mock(DefaultTenantMembershipOutboxMapper.class);
        TenantMembershipClient membershipClient =
                mock(TenantMembershipClient.class);
        DefaultTenantMembershipOutbox outbox =
                new DefaultTenantMembershipOutbox();
        outbox.setOutboxId(17L);
        outbox.setEventId("event-p14-user-17");
        outbox.setUserId(7L);
        outbox.setRole(2);
        outbox.setRetryCount(0);

        when(outboxMapper.selectOne(any())).thenReturn(outbox);
        when(outboxMapper.claim(
                anyLong(),
                anyString(),
                any(),
                any(),
                anyInt())).thenReturn(1);
        when(outboxMapper.selectById(17L)).thenReturn(outbox);
        when(outboxMapper.finalizeFailed(
                anyLong(),
                anyString(),
                any(),
                anyString(),
                anyInt())).thenReturn(1);
        when(membershipClient.ensureDefaultMembership(any()))
                .thenThrow(new IllegalStateException(CANARY_SECRET));

        Logger logger = (Logger) LoggerFactory.getLogger(
                DefaultTenantMembershipOutboxServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            DefaultTenantMembershipOutboxServiceImpl service =
                    new DefaultTenantMembershipOutboxServiceImpl(
                            outboxMapper,
                            membershipClient,
                            new NoOpTransactionManager());
            service.dispatchForUser(7L);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        StringBuilder renderedLogs = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            renderedLogs.append(event.getFormattedMessage());
        }
        assertFalse(renderedLogs.toString().contains(CANARY_SECRET));
        assertTrue(renderedLogs.toString()
                .contains("java.lang.IllegalStateException"));

        ArgumentCaptor<String> errorCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(outboxMapper).finalizeFailed(
                anyLong(),
                anyString(),
                any(),
                errorCaptor.capture(),
                anyInt());
        assertEquals(
                "TENANT_MEMBERSHIP_DELIVERY_FAILED",
                errorCaptor.getValue());
    }

    @Test
    void webSupportAndDatabaseReadinessAreEnabled() throws IOException {
        new WebApplicationContextRunner()
                .withUserConfiguration(
                        ServletWebSupportAutoConfiguration.class)
                .run(context -> {
                    assertEquals(
                            1,
                            context.getBeansOfType(
                                    ApiErrorResponseFactory.class).size());
                    assertEquals(
                            1,
                            context.getBeansOfType(
                                    LowCardinalityTagPolicy.class).size());
                    FilterRegistrationBean<?> registration =
                            context.getBean(
                                    "requestIdFilterRegistration",
                                    FilterRegistrationBean.class);
                    assertTrue(registration.getFilter()
                            instanceof RequestIdFilter);
                });

        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load(
                        "userApplication",
                        new ClassPathResource("application.yml"));
        Object readiness = sources.get(0).getProperty(
                "management.endpoint.health.group.readiness.include");
        assertEquals("readinessState,db", readiness);
    }
}
