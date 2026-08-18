package com.plagod.audit;

import com.plagod.security.TrustedRequestContextResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ClassUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class AuditStarterConsumerBoundaryTest {

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withPropertyValues(
                            "spring.autoconfigure.exclude="
                                    + "com.plagod.security."
                                    + "TrustedRequestAutoConfiguration")
                    .withUserConfiguration(ConsumerProbe.class);

    @Test
    void receivesOnlyTheControlledAuditAppendIntegration() {
        contextRunner.run(context -> {
            assertEquals(1, context.getBeansOfType(AuditWriter.class).size());
            assertEquals(1, context.getBeansOfType(AuditAspect.class).size());
            assertFalse(ClassUtils.isPresent(
                    "com.plagod.mapper.AuditLogMapper",
                    context.getClassLoader()));
            assertFalse(ClassUtils.isPresent(
                    "com.plagod.entity.monitor.AuditLog",
                    context.getClassLoader()));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class
    })
    static class ConsumerProbe {

        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        TrustedRequestContextResolver trustedRequestContextResolver() {
            return mock(TrustedRequestContextResolver.class);
        }
    }
}
