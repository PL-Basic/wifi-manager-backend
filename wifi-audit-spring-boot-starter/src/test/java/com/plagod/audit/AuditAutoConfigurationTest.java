package com.plagod.audit;

import com.plagod.security.TrustedRequestAutoConfiguration;
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

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AuditAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withUserConfiguration(AutoConfigurationProbe.class);

    @Test
    void discoversAppendOnlyAuditComponents() {
        contextRunner.run(context -> {
            assertEquals(1, context.getBeansOfType(AuditWriter.class).size());
            assertEquals(1, context.getBeansOfType(AuditAspect.class).size());
            assertEquals(
                    1,
                    context.getBeansOfType(
                            AfterCommitAuditWriter.class).size());
            assertEquals(
                    1,
                    context.getBeansOfType(
                            IndependentAuditWriter.class).size());
            assertEquals(
                    1,
                    context.getBeansOfType(
                            AuditWriteFailureReporter.class).size());
            assertTrue(context.getBean(AuditWriter.class)
                    instanceof JdbcAuditWriter);
            assertEquals(
                    AuditAutoConfiguration.class.getName(),
                    context.getBeanFactory()
                            .getBeanDefinition("auditWriter")
                            .getFactoryBeanName());

            assertFalse(Modifier.isPublic(AuditWriter.class.getModifiers()));
            assertFalse(Modifier.isPublic(
                    AuditWriteRecord.class.getModifiers()));
            assertFalse(Modifier.isPublic(
                    JdbcAuditWriter.class.getModifiers()));
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
            JdbcTemplateAutoConfiguration.class,
            TrustedRequestAutoConfiguration.class
    })
    static class AutoConfigurationProbe {

        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        TrustedRequestContextResolver
        trustedRequestContextResolver() {
            return new TrustedRequestContextResolver();
        }
    }
}
